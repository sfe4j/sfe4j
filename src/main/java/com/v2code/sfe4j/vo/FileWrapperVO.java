package com.v2code.sfe4j.vo;

import com.v2code.sfe4j.exception.Sfe4jRuntimeException;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileWrapperVO extends BaseVO implements Comparable<FileWrapperVO> {
    private static final long serialVersionUID = -3043572642440858281L;
    private static final boolean IS_WINDOWS = SystemUtils.OS_NAME.toLowerCase().indexOf("windows") >= 0;
    private File file;
    
    public FileWrapperVO(File file) {
        Validate.notNull(file, "Null file not allowed.");
        this.file = file;
    }
    
    public String getName() {
        if (this.file.getParent() == null) {
            return this.file.getPath().replace('\\', '/');
        }
        return this.file.getName();
    }
    
    public String getPath() {
        return this.file.getPath().replace('\\', '/');
    }
    
    public String getFullName() {
        return this.file.getAbsolutePath().replace('\\', '/');
    }
    
    public boolean isWritable() {
        return this.file.canWrite();
    }
    
    public boolean isReadable() {
        return this.file.canRead();
    }
    
    public boolean isExecutable() {
        if (IS_WINDOWS && localCanExecute(this.file) && (this.file.getName().endsWith(".cmd") || this.file.getName().endsWith(".bat"))) {
            return true;
        }
        else if (IS_WINDOWS) { // canExecute is not reliable on Windows -;)
            return false;
        }
        else return localCanExecute(this.file);
    }

    private boolean localCanExecute(File file) {
        if (SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_6)) {
            try {
                Method canExecuteMethod = File.class.getMethod("canExecute", (Class[])null);
                return (Boolean)canExecuteMethod.invoke(file,(Object[]) null);
            } catch (Exception e) {
                throw new Sfe4jRuntimeException("Can't execute File.canExecute()", e);
            } 
        }
        
        return false;
    }
    
    public String getAccessAttributes() {
        String fileAttrs = "";
        if (file.isDirectory()) {
          fileAttrs = fileAttrs + "d";
        }
        else fileAttrs = fileAttrs + "-";
      
        if (this.localCanExecute(file) ) {
          fileAttrs = fileAttrs + "x";
        }
        else fileAttrs = fileAttrs + "-";
      
        if (file.canRead()) {
          fileAttrs = fileAttrs + "r";
        }
        else fileAttrs = fileAttrs + "-";
      
        if (file.canWrite()) {
          fileAttrs = fileAttrs + "w";
        }
        else fileAttrs = fileAttrs + "-";
        
        return fileAttrs;
    }
    
    public String getSize() {
    	if (file.length() > 0L && file.length() < 1024 )
    		return "1 KB";
        return NumberFormat.getIntegerInstance().format(file.length() / 1024) + " KB";
    }
    
    public String getDateTime() {
        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy h:mm a");
        return format.format(new Date(file.lastModified()));
    }

    public int compareTo(FileWrapperVO o) {
        return this.file.compareTo(o.file);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        FileWrapperVO rhs = null;
        if (obj instanceof FileWrapperVO) {
            rhs = (FileWrapperVO)obj;
            return this.file.equals(rhs.file);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.file.hashCode();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return new FileWrapperVO(this.file);
    }

}
