package com.hedera.fileWatcher;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.hedera.utilities.Utility;

import java.io.File;
import java.io.IOException;

public abstract class FileWatcher
{
	private static final Logger log = LogManager.getLogger("filewatcher");
	private static final Marker MARKER = MarkerManager.getMarker("WATCH");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	private final File pathToWatch;

    public FileWatcher(File pathToWatch) {
    	
        this.pathToWatch = pathToWatch;
    	if (! this.pathToWatch.exists()) {
    		this.pathToWatch.mkdirs();
    	}
    }

    public void watch() {
    	while (true) {
	        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
	            Path path = pathToWatch.toPath();
	            path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.OVERFLOW);
	
	            WatchKey key;
	            try { 
	        		key = watcher.poll(100, TimeUnit.MILLISECONDS); 
	            } catch (InterruptedException e) { 
	            	return; 
	            }

				if (Utility.checkStopFile()) {
					log.info(MARKER, "Stop file found, stopping.");
					return;
				}
	            
	            if (key == null) { 
	            	continue; 
	            }
	            
	            for (WatchEvent<?> event : key.pollEvents()) {
	                WatchEvent.Kind<?> kind = event.kind();
	
                	onCreate();

	                boolean valid = key.reset();
	                if (!valid) { 
	                	break; 
	                }
	            }
	        } catch (IOException e1) {
	            log.error(MARKER, "Exception : {}", e1);
			}
	    }
    }

    public abstract void onCreate();
}