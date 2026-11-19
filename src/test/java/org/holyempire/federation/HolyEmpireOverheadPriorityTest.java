package org.holyempire.federation;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireOverheadPriorityTest {
 @Test public void noOtherHighlightAllowsHolyEmpire(){assertTrue(HolyEmpireOverheadPriority.mayDraw(false,false,false,false,false));}
 @Test public void partyHighlightWins(){assertFalse(HolyEmpireOverheadPriority.mayDraw(true,false,false,false,false));}
 @Test public void friendHighlightWins(){assertFalse(HolyEmpireOverheadPriority.mayDraw(false,true,false,false,false));}
 @Test public void friendsChatHighlightWins(){assertFalse(HolyEmpireOverheadPriority.mayDraw(false,false,true,false,false));}
 @Test public void teamHighlightWins(){assertFalse(HolyEmpireOverheadPriority.mayDraw(false,false,false,true,false));}
 @Test public void clanHighlightWins(){assertFalse(HolyEmpireOverheadPriority.mayDraw(false,false,false,false,true));}
}
