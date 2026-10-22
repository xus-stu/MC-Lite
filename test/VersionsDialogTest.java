package mclite;
import javax.imageio.ImageIO; import javax.swing.*; import java.awt.*; import java.awt.image.*; import java.io.*; import java.nio.file.*;
public class VersionsDialogTest { public static void main(String[] a)throws Exception{ Log.init(Path.of("/tmp/vd.log")); var entries=LocalVersions.scan(new GamePaths(testGameDir())); SwingUtilities.invokeAndWait(()->{ JFrame owner=new JFrame(); owner.setSize(300,200); owner.setVisible(true); var d=new VersionsDialog(owner,"切换启动版本",entries,e->{}); Timer t=new Timer(700,e->{ try{ var img=new BufferedImage(d.getContentPane().getWidth(),d.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB); var g=img.createGraphics(); d.getContentPane().paint(g); g.dispose(); ImageIO.write(img,"png",new File("build/versions-dialog.png")); }catch(Exception ex){ex.printStackTrace();} d.dispose(); owner.dispose(); System.exit(0); }); t.setRepeats(false); t.start(); d.setVisible(true); }); } 
    static Path testGameDir() {
        return Path.of(System.getProperty("mclite.test.gameDir",
                Path.of(System.getProperty("user.home"), ".minecraft").toString()));
    }
}
