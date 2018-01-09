/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nolanlab.scaleinvdbc;

/**
 *
 * @author Nikolay Samusik
 */
public class testr {

    public static void main(String[] args) {
        String property = "java.io.tmpdir";

        String tempDir = System.getProperty(property);
        System.out.println("OS current temporary directory is " + tempDir);

    }
}
