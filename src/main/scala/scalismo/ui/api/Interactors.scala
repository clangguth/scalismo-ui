package scalismo.ui.api

import java.awt.Color

import scalismo.ui.control.interactor.landmark.complex.ComplexLandmarkingInteractor
import scalismo.ui.control.interactor.landmark.complex.posterior.PosteriorLandmarkingInteractor
import scalismo.ui.control.interactor.{ DefaultInteractor, Interactor }
import scalismo.ui.model._
import scalismo.ui.view.ScalismoFrame
import scalismo.geometry._

private[api] sealed trait SimpleInteractor {
  type ConcreteInteractor <: Interactor
  val ui: ScalismoUI

  protected[api] def peer: ConcreteInteractor

  ui.frame.interactor = peer
  peer.onActivated(ui.frame)

}

case class SimplePosteriorLandmarkingInteractor(ui: ScalismoUI, modelGroup: Group, targetGroup: Group) extends SimpleInteractor {

  type ConcreteInteractor = PosteriorLandmarkingInteractor

  override protected[api] lazy val peer = new PosteriorLandmarkingInteractor {

    val meshView = ui.find[TriangleMeshView](modelGroup, (p: TriangleMeshView) => true).get
    val shapeTransformationView = ui.find[DiscreteLowRankGPTransformationView](modelGroup, (p: DiscreteLowRankGPTransformationView) => true).get

    private val previewGroup = Group(ui.frame.scene.groups.add("__preview__", ghost = true))

    // we start by copying all transformations of the modelGroup into the previewGroup. The order is important
    modelGroup.peer.transformations.reverse.foreach { transNode =>
      previewGroup.peer.transformations.add(transNode.transformation.asInstanceOf[PointTransformation], transNode.name)
    }

    override val previewNode: TriangleMeshNode = ui.show(previewGroup, meshView.triangleMesh, "previewMesh").peer
    previewNode.visible = false
    previewNode.color.value = Color.YELLOW
    previewNode.pickable.value = false

    override val targetUncertaintyGroup = Group(ui.frame.scene.groups.add("__target_preview__", ghost = true)).peer
    targetUncertaintyGroup

    override def sourceGpNode: TransformationNode[DiscreteLowRankGpPointTransformation] =
      ui.find[DiscreteLowRankGPTransformationView](modelGroup, (p: DiscreteLowRankGPTransformationView) => true).get.peer

    override def targetGroupNode: GroupNode = targetGroup.peer

    override val previewGpNode: TransformationNode[DiscreteLowRankGpPointTransformation] = {
      ui.find[DiscreteLowRankGPTransformationView](previewGroup, (tv: DiscreteLowRankGPTransformationView) => true).get.peer
    }

    override def frame: ScalismoFrame = ui.frame

    override val inversePoseTransform = ui.filter[RigidTransformationView](modelGroup, (rv: RigidTransformationView) => true).reverse.foldLeft((p: Point[_3D]) => p) {
      case (a, b) =>
        (p: Point[_3D]) => a(b.transformation.inverse(p))
    }

  }
}

case class SimpleLandmarkingInteractor(ui: ScalismoUI) extends SimpleInteractor {

  override type ConcreteInteractor = Instance

  private[api] class Instance(override val frame: ScalismoFrame) extends DefaultInteractor with ComplexLandmarkingInteractor[Instance] {}

  override protected[api] lazy val peer: Instance = new Instance(ui.frame)
}
