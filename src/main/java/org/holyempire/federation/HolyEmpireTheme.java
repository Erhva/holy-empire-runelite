package org.holyempire.federation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

/** Restrained imperial-gothic Swing palette. Player-indicator colors are separate. */
final class HolyEmpireTheme {
 static final Color BLUE=new Color(152,200,248);
 static final Color GOLD=new Color(205,170,99);
 static final Color GOLD_EDGE=new Color(133,111,69);
 static final Color STEEL=new Color(29,35,44);
 static final Color PANEL=new Color(37,44,55);
 static final Color HOVER=new Color(53,68,86);
 static final Color SELECTED=new Color(44,69,91);
 static final Color TEXT=new Color(235,237,236);
 static final Color MUTED=new Color(175,189,203);
 static final Color CRIMSON=new Color(173,69,76);
 static final Color CRIMSON_EDGE=new Color(120,60,65);

 private HolyEmpireTheme(){}

 static void styleSearch(JTextField field){
  field.setBackground(new Color(25,32,40));
  field.setForeground(TEXT);
  field.setCaretColor(BLUE);
  field.setSelectionColor(SELECTED);
  field.setSelectedTextColor(TEXT);
  field.setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(GOLD_EDGE),
    BorderFactory.createEmptyBorder(4,7,4,7)));
 }

 /** A low-height framed ceremonial title, shared by all header screens. */
 static JPanel banner(JLabel title,String subtitle,boolean restricted){
  JPanel panel=new JPanel();
  panel.setLayout(new javax.swing.BoxLayout(panel,javax.swing.BoxLayout.Y_AXIS));
  panel.setOpaque(true);
  panel.setBackground(STEEL);
  panel.setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(1,1,2,1,restricted?CRIMSON_EDGE:GOLD_EDGE),
    BorderFactory.createEmptyBorder(6,4,6,4)));
  title.setForeground(HolyEmpireRankDisplay.GOLD);
  title.setFont(title.getFont().deriveFont(Font.BOLD,16f));
  title.setHorizontalAlignment(SwingConstants.CENTER);
  title.setAlignmentX(Component.CENTER_ALIGNMENT);
  title.setMaximumSize(new Dimension(Integer.MAX_VALUE,title.getPreferredSize().height));
  panel.add(title);
  JLabel motto=new JLabel(subtitle,SwingConstants.CENTER);
  motto.setName(restricted?"holyEmpireRestrictedSubtitle":"holyEmpireImperialMotto");
  motto.setFont(motto.getFont().deriveFont(Font.BOLD,10f));
  motto.setForeground(restricted?new Color(222,153,150):BLUE);
  motto.setAlignmentX(Component.CENTER_ALIGNMENT);
  motto.setMaximumSize(new Dimension(Integer.MAX_VALUE,motto.getPreferredSize().height));
  panel.add(motto);
  return panel;
 }

 static void styleRow(JPanel row,boolean emperor,boolean restricted){
  row.setOpaque(true);
  row.setBackground(emperor?new Color(57,51,38):PANEL);
  row.setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(0,emperor?3:2,1,0,
      emperor?GOLD:restricted?CRIMSON_EDGE:GOLD_EDGE),
    BorderFactory.createEmptyBorder(5,7,5,6)));
  row.putClientProperty("holyEmpireThemeRole",
    emperor?"emperor":restricted?"restrictedRow":"rosterRow");
 }

 static void styleSection(JLabel label){
  label.setForeground(GOLD);
  label.setFont(label.getFont().deriveFont(Font.BOLD));
 }

 static JButton button(String text,boolean restricted){
  JButton button=new JButton(text){
   @Override protected void paintComponent(Graphics graphics){
    paintSurface(graphics,this,restricted);
    super.paintComponent(graphics);
   }
  };
  configure(button,restricted);
  return button;
 }

 static JToggleButton tab(String text,boolean restricted){
  JToggleButton button=new JToggleButton(text){
   @Override protected void paintComponent(Graphics graphics){
    paintSurface(graphics,this,restricted);
    super.paintComponent(graphics);
   }
  };
  configure(button,restricted);
  return button;
 }

 private static void paintSurface(Graphics graphics,AbstractButton button,boolean restricted){
  Graphics2D g=(Graphics2D)graphics.create();
  try{
   boolean hover=button.getModel().isRollover() ||
     Boolean.TRUE.equals(button.getClientProperty("holyEmpireHover"));
   Color fill=button.isSelected()?SELECTED:hover?HOVER:PANEL;
   if(!button.isEnabled())fill=STEEL;
   g.setColor(fill);
   g.fillRect(0,0,button.getWidth(),button.getHeight());
   g.setColor(restricted?CRIMSON:button.isSelected()?BLUE:GOLD_EDGE);
   g.fillRect(0,0,3,button.getHeight());
   if(button.isSelected()){
    g.setColor(BLUE);
    g.fillRect(3,button.getHeight()-2,Math.max(0,button.getWidth()-3),2);
   }
  }finally{g.dispose();}
 }

 private static Border buttonBorder(boolean hover,boolean restricted){
  return BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(hover?GOLD:restricted?CRIMSON_EDGE:GOLD_EDGE),
    BorderFactory.createEmptyBorder(6,8,6,8));
 }

 private static void configure(AbstractButton button,boolean restricted){
  button.setForeground(TEXT);
  button.setFont(button.getFont().deriveFont(Font.BOLD));
  button.setContentAreaFilled(false);
  button.setOpaque(false);
  button.setFocusPainted(false);
  button.setRolloverEnabled(true);
  button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  button.putClientProperty("holyEmpireThemeRole",restricted?"restricted":"imperial");
  button.putClientProperty("holyEmpireHover",Boolean.FALSE);
  button.setBorder(buttonBorder(false,restricted));
  button.addMouseListener(new MouseAdapter(){
   private void hover(boolean active){
    button.putClientProperty("holyEmpireHover",active);
    button.setBorder(buttonBorder(active,restricted));
    button.repaint();
   }
   @Override public void mouseEntered(MouseEvent e){hover(true);}
   @Override public void mouseExited(MouseEvent e){hover(false);}
  });
 }
}
