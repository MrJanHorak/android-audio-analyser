package com.example.audioanalyser

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {
    private val valid = """{"formatVersion":1,"preferences":{"dbOffset":30,"noiseThreshold":25},"savedTargetCurves":[{"id":"saved_curve_1","name":"Hall","points":[3,3,2,1,0,-1,-2,-4]}]}"""

    @Test fun acceptsCurrentExportAndExplicitlyEmptyVenueList() {
        assertTrue(isValidAnalyzerBackup(JSONObject(valid)))
        assertTrue(isValidAnalyzerBackup(JSONObject("""{"formatVersion":1,"preferences":{},"savedTargetCurves":[]}""")))
    }

    @Test fun rejectsUnrelatedAndFutureVersionJson() {
        assertFalse(isValidAnalyzerBackup(JSONObject("{}")))
        assertFalse(isValidAnalyzerBackup(JSONObject(valid.replace("\"formatVersion\":1", "\"formatVersion\":4"))))
        assertFalse(isValidAnalyzerBackup(JSONObject(valid.replace("\"preferences\":{", "\"preference_typo\":{"))))
    }

    @Test fun rejectsIncompleteOrDuplicateCurvesRatherThanSilentlyDroppingThem() {
        assertFalse(isValidAnalyzerBackup(JSONObject(valid.replace("[3,3,2,1,0,-1,-2,-4]", "[3,3]"))))
        assertFalse(isValidAnalyzerBackup(JSONObject(valid.replace("[3,3,2,1,0,-1,-2,-4]", "[3,3,2,1,0,-1,-2,99]"))))
        val duplicate = """{"formatVersion":1,"preferences":{},"savedTargetCurves":[{"id":"x","points":[0,0,0,0,0,0,0,0]},{"id":"x","points":[0,0,0,0,0,0,0,0]}]}"""
        assertFalse(isValidAnalyzerBackup(JSONObject(duplicate)))
    }
}
