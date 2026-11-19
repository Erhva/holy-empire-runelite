package org.holyempire.federation;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Keep plugin dialogs modal to RuneLite, but center on its actual monitor. */
final class HolyEmpireDialogs {
 private HolyEmpireDialogs(){}

 /** Pure layout helper; handles monitors with negative virtual screen coordinates. */
 static Point center(Rectangle display,Insets insets,Dimension size){
  int left=display.x+insets.left;
  int top=display.y+insets.top;
  int width=Math.max(1,display.width-insets.left-insets.right);
  int height=Math.max(1,display.height-insets.top-insets.bottom);
  return new Point(left+Math.max(0,(width-size.width)/2),
    top+Math.max(0,(height-size.height)/2));
 }

 private static Point location(Component parent,Dimension size){
  GraphicsConfiguration config=parent==null?null:parent.getGraphicsConfiguration();
  if(config==null){
   java.awt.Window owner=parent==null?null:SwingUtilities.getWindowAncestor(parent);
   if(owner!=null)config=owner.getGraphicsConfiguration();
  }
  if(config==null)
   config=GraphicsEnvironment.getLocalGraphicsEnvironment()
     .getDefaultScreenDevice().getDefaultConfiguration();
  Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(config);
  return center(config.getBounds(),insets,size);
 }

 private static int show(Component parent,Object message,String title,int optionType,
    int messageType,Icon icon,Object[] options,Object initialValue){
  JOptionPane pane=new JOptionPane(message,messageType,optionType,icon,options,initialValue);
  JDialog dialog=pane.createDialog(parent,title);
  try{
   dialog.setLocation(location(parent,dialog.getSize()));
   dialog.setVisible(true);
   Object result=pane.getValue();
   if(result==null || JOptionPane.UNINITIALIZED_VALUE.equals(result))
    return JOptionPane.CLOSED_OPTION;
   if(options==null){
    return result instanceof Integer?(Integer)result:JOptionPane.CLOSED_OPTION;
   }
   for(int i=0;i<options.length;i++)
    if(options[i].equals(result))return i;
   return JOptionPane.CLOSED_OPTION;
  }finally{
   dialog.dispose();
  }
 }

 static void showMessageDialog(Component parent,Object message,String title,int messageType){
  show(parent,message,title,JOptionPane.DEFAULT_OPTION,messageType,null,null,null);
 }

 static int showConfirmDialog(Component parent,Object message,String title,int optionType){
  return showConfirmDialog(parent,message,title,optionType,JOptionPane.QUESTION_MESSAGE);
 }

 static int showConfirmDialog(Component parent,Object message,String title,
    int optionType,int messageType){
  return show(parent,message,title,optionType,messageType,null,null,null);
 }

 static int showOptionDialog(Component parent,Object message,String title,int optionType,
    int messageType,Icon icon,Object[] options,Object initialValue){
  return show(parent,message,title,optionType,messageType,icon,options,initialValue);
 }
}
