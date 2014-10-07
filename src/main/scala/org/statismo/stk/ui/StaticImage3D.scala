package org.statismo.stk.ui

import java.io.File

import org.statismo.stk.core.common.ScalarValue
import org.statismo.stk.core.image.DiscreteScalarImage3D
import org.statismo.stk.core.io.ImageIO
import org.statismo.stk.ui.Reloadable.{FileReloader, Reloader}

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag
import scala.util.{Failure, Success, Try}

object StaticImage3D extends SceneTreeObjectFactory[StaticImage3D[_]] with FileIoMetadata {
  override val description = "Static 3D Image"
  override val fileExtensions = immutable.Seq("nii", "nia", "vtk")
  protected[ui] override val ioMetadata = this

  protected[ui] override def tryCreate(file: File)(implicit scene: Scene): Try[StaticImage3D[_]] = {
    createFromFile(file, None, file.getName)
  }

  def createFromFile(file: File, parent: Option[StaticThreeDObject], name: String)(implicit scene: Scene): Try[StaticImage3D[_]] = {
    {
      // Short
      val peerTry = ImageIO.read3DScalarImage[Short](file)
      if (peerTry.isSuccess) {
        val reloader = new FileReloader[DiscreteScalarImage3D[Short]](file) {
          override def load() = ImageIO.read3DScalarImage[Short](file)
        }
        return Success(new StaticImage3D(peerTry.get, Some(reloader), parent, Some(name)))
      }
    }
    {
      // Float
      val peerTry = ImageIO.read3DScalarImage[Float](file)
      if (peerTry.isSuccess) {
        val reloader = new FileReloader[DiscreteScalarImage3D[Float]](file) {
          override def load() = ImageIO.read3DScalarImage[Float](file)
        }
        return Success(new StaticImage3D(peerTry.get, Some(reloader), parent, Some(name)))
      }
    }
    {
      // Double
      val peerTry = ImageIO.read3DScalarImage[Double](file)
      if (peerTry.isSuccess) {
        val reloader = new FileReloader[DiscreteScalarImage3D[Double]](file) {
          override def load() = ImageIO.read3DScalarImage[Double](file)
        }
        return Success(new StaticImage3D(peerTry.get, Some(reloader), parent, Some(name)))
      }
    }
    Failure(new IllegalArgumentException("could not load " + file.getAbsoluteFile))
  }

  def createFromPeer[S: ScalarValue : ClassTag : TypeTag](peer: DiscreteScalarImage3D[S], parent: Option[StaticThreeDObject] = None, name: Option[String] = None)(implicit scene: Scene): StaticImage3D[S] = {
    new StaticImage3D(peer, None, parent, name)
  }
}

class StaticImage3D[S: ScalarValue : ClassTag : TypeTag] private[StaticImage3D](initialPeer: DiscreteScalarImage3D[S], reloaderOption: Option[Reloader[DiscreteScalarImage3D[S]]], initialParent: Option[StaticThreeDObject] = None, name: Option[String] = None)(implicit override val scene: Scene) extends Image3D[S](initialPeer, reloaderOption) {
  name_=(name.getOrElse(Nameable.NoName))
  override lazy val parent: StaticThreeDObject = initialParent.getOrElse(new StaticThreeDObject(Some(scene.staticObjects), name))

  parent.representations.add(this)
}