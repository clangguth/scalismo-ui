package org.statismo.stk.ui.vtk

import java.awt.BorderLayout
import scala.swing.Component
import scala.swing.Reactor
import org.statismo.stk.ui.Viewport
import javax.swing.JPanel
import org.statismo.stk.ui.Workspace
import scala.swing.Swing
import java.io.File
import vtk.vtkWindowToImageFilter
import vtk.vtkPNGWriter
import scala.util.Try

class VtkPanel(workspace: Workspace, viewport: Viewport) extends Component with Reactor {
  lazy val ui = new VtkCanvas(workspace, viewport)
  override lazy val peer = {
    val panel = new JPanel(new BorderLayout())
    panel.add(ui, BorderLayout.CENTER);
    panel
  }
  lazy val vtk = new VtkViewport(viewport, ui.GetRenderer(), ui.interactor)
  listenTo(viewport, vtk)
  if (!workspace.scene.displayables.filter(d => d.isShownInViewport(viewport)).isEmpty) {
    Swing.onEDT(ui.Render())
  }

  reactions += {
    case Viewport.Destroyed(v) => {
      deafTo(viewport, vtk)
    }
    case VtkContext.RenderRequest(s) => {
      ui.Render()
    }
    case VtkContext.ViewportEmpty(v) => {
      ui.setAsEmpty()
    }
  }

  def resetCamera() = {
    vtk.resetCamera()
  }

  def screenshot(file: File) = Try {
    val filter = new vtkWindowToImageFilter
    filter.SetInput(ui.GetRenderWindow())
    filter.SetInputBufferTypeToRGBA()
    filter.Update()

    val writer = new vtkPNGWriter
    writer.SetFileName(file.getAbsolutePath())
    writer.SetInputConnection(filter.GetOutputPort())
    writer.Write()
    writer.Delete()
    filter.Delete()
  }
}