package mclite;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
public final class DialogTest {
 public static void main(String[] a) throws Exception {
  Theme.install();
  Log.init(java.nio.file.Path.of("/tmp/mclite-dialog-test.log"));
  Config config=Config.load();
  SwingUtilities.invokeAndWait(() -> {
   JFrame owner=new JFrame(); owner.setSize(200,100); owner.setLocationRelativeTo(null); owner.setVisible(true);
   AccountsDialog d=new AccountsDialog(owner, config);
   Timer t=new Timer(1000,e->{
    try {
     JPanel content=(JPanel)d.getContentPane();
     BufferedImage img=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);
     Graphics2D g=img.createGraphics(); content.paint(g); g.dispose();
     ImageIO.write(img,"png",new File("build/dialog.png"));
    } catch(Exception ex){ex.printStackTrace();}
    d.dispose();
   }); t.setRepeats(false); t.start();
   d.setVisible(true);
   owner.dispose();
  });
  System.out.println("dialog ok");
 }
}
