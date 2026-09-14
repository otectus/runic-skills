package com.otectus.runicskills.common.progression;
import com.otectus.runicskills.common.util.LevelCapMath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LevelCapMathTest {
    @Test void automaticTracksRegisteredSkillsAndPreservesCustomChoice() {
        for(int cap:new int[]{2,16,32,64,100,1000}) {
            assertEquals(10*cap,LevelCapMath.effective("sum_of_skill_caps",256,10,cap));
            assertEquals(11*cap,LevelCapMath.effective("sum_of_skill_caps",256,11,cap));
            assertEquals(1024,LevelCapMath.effective("custom",1024,10,cap));
        }
        assertEquals(103,LevelCapMath.suggestedSkillCap(1024,10));
        assertEquals(Integer.MAX_VALUE,LevelCapMath.reachable(Integer.MAX_VALUE,1000));
    }
}
