package org.edla.tmdb.shelf

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.FileSystemFile

import java.io.{File, FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.Paths

object Sync {

  val testFileName = "/tmp/test"
  val testFile     = new File(testFileName)
  val syncFileName = Paths.get(s"${Launcher.tmpDir}/tmdb_sync")

  def upload(lastSeenMovies: Seq[MovieDB], client: SSHClient): Unit = {
    val tmpFile = s"${Launcher.tmpDir}${File.separator}.tmdb.temp"
    val oos     = new ObjectOutputStream(new FileOutputStream(tmpFile))
    oos.writeObject(lastSeenMovies)
    oos.close
    client.newSCPFileTransfer.upload(new FileSystemFile(tmpFile), "./.tmdb.ser")
    client.close()
  }

  def download(client: SSHClient): Seq[MovieDB] = {
    client.newSCPFileTransfer().download(".tmdb.ser", new FileSystemFile(s"${Launcher.tmpDir}${File.separator}"))
    client.close()
    val ois            = new ObjectInputStream(new FileInputStream(s"${Launcher.tmpDir}${File.separator}.tmdb.ser"))
    val lastSeenMovies = ois.readObject.asInstanceOf[Seq[MovieDB]]
    ois.close
    lastSeenMovies
  }

}
