package com.apigee.proxywriter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GenerateBundle {

	private static final int BUFFER = 4096;

	private static final Logger LOGGER = Logger.getLogger(GenerateBundle.class.getName());
	private static final ConsoleHandler handler = new ConsoleHandler();
	

	static {
		LOGGER.setLevel(Level.WARNING);
		// PUBLISH this level
		handler.setLevel(Level.WARNING);
		LOGGER.addHandler(handler);
	}	
	
	
	/*public static File build(String zipFolder, String proxyName) throws Exception{
		LOGGER.entering(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
        File f;
		try {
            f =  zipIt(proxyName + ".zip", zipFolder, generateFileList(new File(zipFolder), zipFolder));
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
			e.printStackTrace();
			throw e;
		}
		LOGGER.exiting(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
        return f;
	}*/
	
    private void addFolderToZip(File folder, String parentFolder,
            ZipOutputStream zos) throws FileNotFoundException, IOException {
		LOGGER.entering(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
    	
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                addFolderToZip(file, parentFolder + "/" + file.getName(), zos);
                continue;
            }
  
            zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
  
            BufferedInputStream bis = new BufferedInputStream(
                    new FileInputStream(file));
  
            long bytesRead = 0;
            byte[] bytesIn = new byte[BUFFER];
            int read = 0;
  
            while ((read = bis.read(bytesIn)) != -1) {
                zos.write(bytesIn, 0, read);
                bytesRead += read;
            }
  
            zos.closeEntry();
            bis.close();
        }
		LOGGER.exiting(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
    }   
    	
	public File build(String zipFolder, String proxyName) throws Exception {
		
		LOGGER.entering(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());

		try {
			File folder = new File(zipFolder);
			String parentFolder = zipFolder.substring(zipFolder.lastIndexOf(File.separator) + 1, zipFolder.length());
			
			File f = new File(folder.getParent(), proxyName + ".zip");
			
			FileOutputStream fos = new FileOutputStream(f);
			ZipOutputStream zos = new ZipOutputStream(fos);
			
			addFolderToZip(folder, parentFolder, zos);  
			       
			zos.flush();  
			zos.close();  

			LOGGER.exiting(GenerateBundle.class.getName(), new Object() {
			}.getClass().getEnclosingMethod().getName());
			
			return f;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

}
