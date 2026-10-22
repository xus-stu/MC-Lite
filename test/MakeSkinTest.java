package mclite;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
public class MakeSkinTest { public static void main(String[] a) throws Exception {
 BufferedImage im=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
 Graphics2D g=im.createGraphics();
 g.setColor(new Color(0x6B4A2F)); g.fillRect(8,8,8,8);
 g.setColor(new Color(0xE0B080)); g.fillRect(9,10,6,5);
 g.setColor(Color.WHITE); g.fillRect(10,11,1,1); g.fillRect(13,11,1,1);
 g.setColor(new Color(0x3355AA)); g.fillRect(10,11,1,1); g.fillRect(13,11,1,1);
 g.setColor(new Color(0x883333)); g.fillRect(11,14,2,1);
 g.setColor(new Color(0x4A8A4A)); g.fillRect(20,20,8,12);
 g.dispose();
 ImageIO.write(im,"png", Path.of("/tmp/face-skin.png").toFile());
 Config c=Config.load(); var d=AccountStore.load(c.accountsDir()); SkinStore.save(c,d.accounts().get(0).id(),Path.of("/tmp/face-skin.png"));
 System.out.println("skin saved");
}}
