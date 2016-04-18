/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Startup;

/**
 *
 * @author Alessandro
 */
public class Configuration {
    static final long maxByteSpace = 1000000000;
    static final String filePath = "files/";
    static final String tmpFilePath = System.getProperty("java.io.tmpdir");

    static public long getMaxByteSpace() {
        return maxByteSpace;
    }

    static public String getFilePath() {
        return filePath;
    }

    static public String getTmpFilePath() {
        return tmpFilePath;
    }
}
