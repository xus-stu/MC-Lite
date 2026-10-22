package mclite;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
public final class TasksDialogTest {
 public static void main(String[] a) throws Exception {
  Theme.install();
  Log.init(java.nio.file.Path.of("/tmp/mclite-tasks-test.log"));
  TaskCenter center=new TaskCenter();
  DownloadTask t=center.start("安装 Minecraft 1.20.1", "Mojang 官方 + BMCLAPI 镜像");
  t.stage("下载资源 assets/objects/ab/abcdef…"); t.progress(48_000_000, 120_000_000);
  DownloadTask t2=center.start("安装 Fabric 加载器", "Fabric Meta");
  t2.stage("完成"); t2.progress(8,8); t2.state(DownloadTask.State.DONE);
  SwingUtilities.invokeAndWait(() -> {
   JFrame owner=new JFrame(); owner.setSize(300,200); owner.setLocationRelativeTo(null); owner.setVisible(true);
   TasksDialog d=new TasksDialog(owner,center,()->{});
   d.setVisible(true);
   d.validate();
   Timer timer=new Timer(1200,e->{
    try {
     JPanel content=(JPanel)d.getContentPane();
     BufferedImage img=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);
     Graphics2D g=img.createGraphics(); content.paint(g); g.dispose();
     ImageIO.write(img,"png",new File("build/tasks-dialog.png"));
    } catch(Exception ex){ex.printStackTrace();}
    d.dispose(); owner.dispose(); System.exit(0);
   });
   timer.setRepeats(false); timer.start();
  });
 }
}
