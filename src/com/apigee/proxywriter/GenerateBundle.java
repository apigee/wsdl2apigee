package com.apigee.proxywriter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
	
	/*private static File zipIt(String zipFile, String targetFolder, List<String> fileList) throws Exception{

		LOGGER.entering(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		byte[] buffer = new byte[BUFFER];
		String source = "";
		File f = new File(new File(targetFolder).getParent(), zipFile);
		FileOutputStream fos = null;
		ZipOutputStream zos = null;
		try {
			try {
				source = targetFolder.substring(targetFolder.lastIndexOf(File.separator) + 1, targetFolder.length());
			} catch (Exception e) {
				source = targetFolder;
			}

			if (f.exists()) {
				File newFile = new File(f.getParent(),
						zipFile.substring(0, zipFile.lastIndexOf(".")) + java.lang.System.currentTimeMillis() + ".zip");
				Files.move(f.toPath(), newFile.toPath());
			}

			fos = new FileOutputStream(f);
			zos = new ZipOutputStream(fos);

			LOGGER.fine("Output to Zip : " + zipFile);

			FileInputStream in = null;

			for (String file : fileList) {
				LOGGER.fine("File Added : " + file);
				ZipEntry ze = new ZipEntry(source + '/' + file);
				zos.putNextEntry(ze);
				try {
					in = new FileInputStream(targetFolder + '/' + file);
					int len;
					while ((len = in.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}
				} finally {
					in.close();
				}
			}

			zos.closeEntry();
			LOGGER.fine("Folder successfully compressed");
            return f;

		} catch (IOException ex) {
			LOGGER.severe(ex.getMessage());
			ex.printStackTrace();
			throw ex;
		} finally {
			try {
				zos.close();
			} catch (IOException e) {
				LOGGER.severe(e.getMessage());
				e.printStackTrace();
				throw e;
			}
		}
	}
	
	private static List<String> generateFileList(File node, String targetFolder) throws Exception{

        final ArrayList<String> fileList = new ArrayList<>();
        // add file only
		if (node.isFile()) {
			fileList.add(generateZipEntry(node.toString(), targetFolder));

		}

		if (node.isDirectory()) {
			String[] subNote = node.list();
			//fileList.add(node.toString());
			for (String filename : subNote) {
				fileList.addAll(generateFileList(new File(node, filename), targetFolder));
			}
		}
        return fileList;
	}

	private static String generateZipEntry(String file, String targetFolder) throws Exception{
		LOGGER.entering(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		LOGGER.fine("File: " + file + " File Length: " + file.length());
		LOGGER.fine("Target Folder: " + targetFolder + " Target Folder length: " + targetFolder.length());
		LOGGER.exiting(GenerateBundle.class.getName(), new Object() {
		}.getClass().getEnclosingMethod().getName());
		return file.substring(targetFolder.length() + 1, file.length());
	}
	
	public static File build(String zipFolder, String proxyName) throws Exception{
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
    }   
    	
	public File build(String zipFolder, String proxyName) throws Exception {
		
		try {
			File folder = new File(zipFolder);
			String parentFolder = zipFolder.substring(zipFolder.lastIndexOf(File.separator) + 1, zipFolder.length());
			
			File f = new File(folder.getParent(), proxyName + ".zip");
			
			FileOutputStream fos = new FileOutputStream(f);
			ZipOutputStream zos = new ZipOutputStream(fos);
			
			addFolderToZip(folder, parentFolder, zos);  
			       
			zos.flush();  
			zos.close();  
			
			return f;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

}
