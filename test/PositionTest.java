package mclite;
import javax.swing.*;
public class PositionTest { public static void main(String[] a)throws Exception{ SwingUtilities.invokeAndWait(()->{ JFrame f=new JFrame(); f.setSize(300,200); f.setVisible(true); LaunchProgress p=new LaunchProgress(f,"test"); p.setVisible(true); System.out.println("screen="+java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds()); System.out.println("dialog="+p.getBounds()); p.dispose(); f.dispose(); System.exit(0); }); } }
