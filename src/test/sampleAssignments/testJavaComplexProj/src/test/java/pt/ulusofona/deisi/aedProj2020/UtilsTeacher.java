package pt.ulusofona.deisi.aedProj2020;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class UtilsTeacher {

    public static void copyFile(File sourceFile, File destFile) {
        try {
            if (!sourceFile.exists()) {
                return;
            }
            if (!destFile.exists()) {
                destFile.createNewFile();
            }
            FileChannel source = null;
            FileChannel destination = null;
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            if (source != null) {
                destination.transferFrom(source, 0, source.size());
            }
            if (source != null) {
                source.close();
            }
            destination.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
