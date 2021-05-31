package com.amoylabs.sfe4j.spring.boot.service;

import com.amoylabs.sfe4j.spring.boot.configuration.Sfe4jConfiguration;
import com.amoylabs.sfe4j.spring.boot.vo.FileTreeVO;
import com.amoylabs.sfe4j.spring.boot.vo.FileVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

@Service
public class FileExplorerService {

    private Sfe4jConfiguration sfe4jConfiguration;

    public FileTreeVO buildFileTree(String dir) {

        String currentDirectoryPath = StringUtils.isNotEmpty(dir) ? dir : sfe4jConfiguration.getBaseDirPath();

        File currentDirectory = new File(currentDirectoryPath);

        Set<FileVO> childDirectories = new TreeSet<>();
        Set<FileVO> files = new TreeSet<>();

        for (File file : currentDirectory.listFiles()) {
            if (file.isDirectory()) {
                childDirectories.add(new FileVO(file));
            } else {
                files.add(new FileVO(file));
            }
        }

        FileTreeVO fileTree = new FileTreeVO();
        fileTree.setCurrentDirectory(new FileVO(currentDirectory));
        if (currentDirectory.getParentFile() != null
                && (!sfe4jConfiguration.getRestrictToBaseDir() || isWithinBase(currentDirectory.getParentFile()))) {
            fileTree.setParentDirectory(new FileVO(currentDirectory.getParentFile()));
        }
        fileTree.setChildDirectories(childDirectories);
        fileTree.setFiles(files);

        return fileTree;
    }

    private boolean isWithinBase(File dir) {
        boolean isWithinBase = false;
        File localDir = dir;
        File baseDir = new File(sfe4jConfiguration.getBaseDirPath());
        if (baseDir.equals(dir)) {
            isWithinBase = true;
        }

        while (!isWithinBase && localDir.getParentFile() != null) {
            if (baseDir.equals(localDir.getParentFile())) {
                isWithinBase = true;
            }
            localDir = localDir.getParentFile();
        }

        return isWithinBase;
    }

    /**
     * Setter
     */
    @Autowired
    public void setSfe4jConfiguration(Sfe4jConfiguration sfe4jConfiguration) {
        this.sfe4jConfiguration = sfe4jConfiguration;
    }
}