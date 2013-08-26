package org.jboss.as.modulardeployer.deployment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.jboss.vfs.TempDir;
import org.jboss.vfs.VirtualFile;

class Module {

    private static final String LIB_PATH = "lib";
    private final File warArchive;
    private final File libFolder;
    private final String name;
    private boolean deployed;
    private boolean started;
    
    public Module(TempDir tempDir, VirtualFile war, String name) throws IOException {
        this.name = name;
        warArchive = new File(tempDir.getRoot(), name);
        copy(war, warArchive);
        libFolder = new File(warArchive, LIB_PATH);
        if (!libFolder.isDirectory()) {
            libFolder.mkdir();
        }
    }

    public void addLibs(List<VirtualFile> libs) throws IOException {
        for (VirtualFile lib : libs) {
            copy(lib, libFolder);
        }
    }

    private void copy(final VirtualFile src, final File dest) throws IOException {
        for (VirtualFile vf : src.getChildren()) {
            if (vf.isFile()) {
                if (!dest.exists()) {
                    if (!dest.mkdirs()) {
                        throw new IOException("Cannot create dir " + dest);
                    }
                }
                
                File destFile = new File(dest, vf.getName());
                copyFile(vf.getPhysicalFile(), destFile);
            } else {
                copy(vf,new File(dest, vf.getName()));
            }
        }
        
//        final Path srcPath = src.getPhysicalFile().toPath();
//        Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>() {
//            @Override
//            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//                copyFile(file.toFile(), new File(dest, file.relativize(srcPath).toString()));
//                return FileVisitResult.CONTINUE;
//            }
//        });
    }

    private void copyFile(File src, File dest) throws FileNotFoundException, IOException {
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(src).getChannel();
            destination = new FileOutputStream(dest).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
    }

    public void markDeployed() {
        deployed = true;
    }

    public boolean isDeployed() {
        return deployed;
    }

    public String name() {
        return name;
    }

    public void markStarterd() {
        started = true;
    }
    
    public boolean isStarted() {
        return started;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        //TODO return ZIP acrhive as stream
        return null;
    }
}
