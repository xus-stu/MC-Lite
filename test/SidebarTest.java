package mclite;
import javax.swing.*;
import java.lang.reflect.*;
public class SidebarTest { public static void main(String[] a) throws Exception {
 SwingUtilities.invokeAndWait(() -> {
  try {
   Config c=Config.load(); c.sidebarCollapsed(true); c.autoJvmTuning(false);
   LauncherFrame f=new LauncherFrame(c); f.setVisible(true);
   Field sf=LauncherFrame.class.getDeclaredField("sidebar"); sf.setAccessible(true);
   JPanel side=(JPanel)sf.get(f);
   Field bf=LauncherFrame.class.getDeclaredField("sidebarToggle"); bf.setAccessible(true);
   JButton b=(JButton)bf.get(f);
   Timer t=new Timer(600,e->{ System.out.println("before visible="+side.isVisible()+" width="+side.getWidth()); b.doClick(); new Timer(350,x->{System.out.println("after visible="+side.isVisible()+" width="+side.getWidth()); f.dispose(); System.exit(0);}).start(); });
   t.setRepeats(false); t.start();
  } catch(Exception ex){ex.printStackTrace(); System.exit(1);}
 });
 } }
