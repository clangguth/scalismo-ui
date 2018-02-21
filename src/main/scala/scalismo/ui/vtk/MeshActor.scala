package scalismo.ui.vtk

import scalismo.geometry.{ Point, _3D }
import scalismo.ui.MeshView.{ MeshRenderable, TriangleMeshRenderable }
import scalismo.ui.ScalarMeshFieldView.ScalarMeshFieldRenderable
import scalismo.ui.visualization.props.{ ColorProperty, LineWidthProperty, OpacityProperty, ScalarRangeProperty }
import scalismo.ui.{ BoundingBox, MeshView, ScalarMeshFieldView, TwoDViewport }
import scalismo.utils.MeshConversion
import vtk._

import scala.swing.Publisher

object MeshActor {
  def apply[T <: Publisher](vtkViewport: VtkViewport, source: MeshRenderable[T]): RenderableActor = {
    vtkViewport.viewport match {
      case vp2d: TwoDViewport => MeshActor2D(vp2d, source)
      case _ => MeshActor3D(source)
    }
  }
}

trait MeshActor[T <: Publisher] extends SinglePolyDataActor with ActorOpacity {
  def renderable: MeshRenderable[T]

  override lazy val opacity: OpacityProperty = renderable.opacity

  protected def meshToVtkPolyData(template: Option[vtkPolyData]): vtkPolyData

  protected var polydata: vtkPolyData = meshToVtkPolyData(None)

  protected def onGeometryChanged(): Unit

  protected def rerender(geometryChanged: Boolean = false, canUseTemplate: Boolean = true) = {
    if (geometryChanged) {
      val template = if (canUseTemplate) Some(polydata) else None
      polydata = meshToVtkPolyData(template)
      onGeometryChanged()
    }

    publishEdt(VtkContext.RenderRequest(this))
  }

  protected def onInstantiated(): Unit = {}

  override def onDestroy() {
    deafTo(renderable.source)
    super.onDestroy()
  }

  reactions += {
    case MeshView.GeometryChanged(m) => rerender(geometryChanged = true)
    case MeshView.Reloaded(m) => rerender(geometryChanged = true, canUseTemplate = false)
  }

  listenTo(renderable.source)

  onInstantiated()

  rerender(geometryChanged = true)
}

object MeshActor2D {
  def apply[T](viewport: TwoDViewport, source: MeshRenderable[T]): RenderableActor = source match {
    case r: TriangleMeshRenderable => new TriangleMeshActor2D(viewport, r)
    case r: ScalarMeshFieldRenderable => new ScalarMeshFieldActor2D(viewport, r)
    case _ => throw new IllegalArgumentException("don't know how to handle " + source.getClass)
  }
}

abstract class MeshActor2D[T <: Publisher](viewport: TwoDViewport, override val renderable: MeshRenderable[T]) extends TwoDSlicingActor(viewport) with MeshActor[T] with ActorLineWidth {
  override def lineWidth: LineWidthProperty = renderable.lineWidth

  override protected def onSlicePositionChanged(): Unit = rerender()

  override protected def onGeometryChanged(): Unit = {
    planeCutter.SetInputData(polydata)
    planeCutter.Modified()
  }

  override protected def sourceBoundingBox: BoundingBox = VtkUtils.bounds2BoundingBox(polydata.GetBounds())
}

object MeshActor3D {
  def apply[T](source: MeshRenderable[T]): RenderableActor = source match {
    case r: TriangleMeshRenderable => new TriangleMeshActor3D(r)
    case r: ScalarMeshFieldRenderable => new ScalarMeshFieldActor3D(r)
    case _ => throw new IllegalArgumentException("don't know how to handle " + source.getClass)
  }
}

abstract class MeshActor3D[T <: Publisher](override val renderable: MeshRenderable[T]) extends SinglePolyDataActor with MeshActor[T] {

  // not declaring this as lazy causes all sorts of weird VTK errors, probably because the methods which use
  // it are invoked from the superclass constructor (at which time this class is not necessarily fully initialized)(?)
  lazy val normals = new vtk.vtkPolyDataNormals() {
    ComputePointNormalsOn()
    ComputeCellNormalsOff()
  }

  override protected def onInstantiated(): Unit = {
    mapper.SetInputConnection(normals.GetOutputPort())
  }

  override protected def onGeometryChanged() = {
    normals.RemoveAllInputs()
    normals.SetInputData(polydata)
    normals.Update()
  }

}

trait TriangleMeshActor extends MeshActor[MeshView] with ActorColor with ClickableActor {
  override def renderable: TriangleMeshRenderable

  override def color: ColorProperty = renderable.color

  override protected def meshToVtkPolyData(template: Option[vtkPolyData]): vtkPolyData = {
    Caches.TriangleMeshCache.getOrCreate(renderable.source.source, MeshConversion.meshToVtkPolyData(renderable.source.source, template))
  }

  override def clicked(point: Point[_3D]): Unit = renderable.source.addLandmarkAt(point)

}

class TriangleMeshActor3D(override val renderable: TriangleMeshRenderable) extends MeshActor3D[MeshView](renderable) with TriangleMeshActor

class TriangleMeshActor2D(viewport: TwoDViewport, override val renderable: TriangleMeshRenderable) extends MeshActor2D[MeshView](viewport, renderable) with TriangleMeshActor with TwoDClickable {

  private class Locator extends vtkPointLocator {
    var isValid = false
  }

  private val locator = new Locator()

  private def initPointLocator(): Unit = {
    // for some reason I don't understand, this needs to (only) be initialized
    // once, then continues to work even if the underlying output changes.
    val poly = planeCutter.GetOutput()
    if (poly.GetNumberOfPoints() > 0) {
      locator.isValid = true
      locator.SetDataSet(poly)
      locator.BuildLocator()
    } else {
      locator.isValid = false
    }
  }

  override def findClosestPoint(clicked: Point[_3D]): Option[Point[_3D]] = {
    if (!locator.isValid) initPointLocator()
    if (locator.isValid) {
      val closest = locator.FindClosestPoint(clicked.toArray.map(_.toDouble))
      if (closest < 0) None
      else {
        val coords = planeCutter.GetOutput().GetPoint(closest)
        Some(Point[_3D](coords.map(_.toFloat)))
      }
    } else None
  }

  override def setHighlight(toggled: Boolean): Unit = {
    val border = if (toggled) 1 else 0
    val newWidth = lineWidth.value.toFloat + border
    if (vtkActor.GetProperty().GetLineWidth() != newWidth) {
      vtkActor.GetProperty().SetLineWidth(newWidth)
      publishEdt(VtkContext.RenderRequest(this))
    }
  }
}

trait ScalarMeshFieldActor extends MeshActor[ScalarMeshFieldView] with ActorScalarRange {
  override def renderable: ScalarMeshFieldRenderable

  override def scalarRange: ScalarRangeProperty = renderable.scalarRange

  override protected def meshToVtkPolyData(template: Option[vtkPolyData]): vtkPolyData = {
    Caches.ScalarMeshFieldCache.getOrCreate(renderable.source.source, MeshConversion.scalarMeshFieldToVtkPolyData(renderable.source.source))
  }

}

class ScalarMeshFieldActor3D(override val renderable: ScalarMeshFieldRenderable) extends MeshActor3D[ScalarMeshFieldView](renderable) with ScalarMeshFieldActor

class ScalarMeshFieldActor2D(viewport: TwoDViewport, override val renderable: ScalarMeshFieldRenderable) extends MeshActor2D[ScalarMeshFieldView](viewport, renderable) with ScalarMeshFieldActor