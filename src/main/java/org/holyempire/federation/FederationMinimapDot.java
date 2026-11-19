package org.holyempire.federation;

import java.awt.Color;
import java.awt.Graphics2D;

/** Match the 4x5 dot silhouette used by RuneLite's built-in Minimap plugin.
 *  Unlike built-in category sprites, this is per-player and doesn't change global map dots.
 */
final class FederationMinimapDot {
 private FederationMinimapDot() {}

 static void draw(Graphics2D graphics, int x, int y, Color color) {
  if (color == null) return;
  final int left=x-2, top=y-2;
  graphics.setColor(color);
  graphics.fillRect(left+1,top,2,1);
  graphics.fillRect(left,top+1,4,2);
  graphics.fillRect(left+1,top+3,2,1);
  graphics.setColor(Color.BLACK);
  graphics.fillRect(left,top+3,1,1);
  graphics.fillRect(left+3,top+3,1,1);
  graphics.fillRect(left+1,top+4,2,1);
 }
}
