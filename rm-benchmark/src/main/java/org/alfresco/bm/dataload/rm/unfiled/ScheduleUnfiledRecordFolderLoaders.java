/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.bm.dataload.rm.unfiled;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.dataload.RmBaseEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Prepare event for unfiled record folders structure
 *
 * @author Silviu Dinuta
 * @since 1.0
 *
 */
public class ScheduleUnfiledRecordFolderLoaders extends RmBaseEventProcessor
{
    public static final String EVENT_NAME_LOAD_UNFILED_RECORD_FOLDERS = "loadUnfiledRecordFolders";
    public static final String EVENT_NAME_SCHEDULE_LOADERS = "scheduleUnfiledFoldersLoaders";
    public static final String EVENT_NAME_LOADING_COMPLETE = "loadingUnfiledRecordFoldersComplete";

    @Autowired
    private SessionService sessionService;

    private int maxActiveLoaders;
    private int unfiledRecordFolderDepth;
    private int unfiledRecordFolderChildrenAverage;
    private int unfiledRecordFolderChildrenVariance;
    private int folderNumberStandardDeviation;
    private boolean createUnfiledRecordFolderStructure;
    private int rootUnfiledRecordFolderNumber;
    private long loadCheckDelay;
    private String username;
    private int maxLevel;
    private String eventNameLoadUnfiledRecordFolders = EVENT_NAME_LOAD_UNFILED_RECORD_FOLDERS;
    private String eventNameScheduleLoaders = EVENT_NAME_SCHEDULE_LOADERS;
    private String eventNameLoadingComplete = EVENT_NAME_LOADING_COMPLETE;

    public int getMaxActiveLoaders()
    {
        return maxActiveLoaders;
    }

    public void setMaxActiveLoaders(int maxActiveLoaders)
    {
        this.maxActiveLoaders = maxActiveLoaders;
    }

    public int getUnfiledRecordFolderDepth()
    {
        return unfiledRecordFolderDepth;
    }

    public void setUnfiledRecordFolderDepth(int unfiledRecordFolderDepth)
    {
        this.unfiledRecordFolderDepth = unfiledRecordFolderDepth;
        this.maxLevel = this.unfiledRecordFolderDepth + 4;      // Add levels for "/Sites<L1>/siteId<L2>/documentLibrary<L3>/Unfiled Records<L4>"
    }

    public int getMaxLevel()
    {
        return maxLevel;
    }

    public int getUnfiledRecordFolderChildrenAverage()
    {
        return unfiledRecordFolderChildrenAverage;
    }

    public void setUnfiledRecordFolderChildrenAverage(int unfiledRecordFolderChildrenAverage)
    {
        this.unfiledRecordFolderChildrenAverage = unfiledRecordFolderChildrenAverage;
    }

    public int getUnfiledRecordFolderChildrenVariance()
    {
        return unfiledRecordFolderChildrenVariance;
    }

    public void setUnfiledRecordFolderChildrenVariance(int unfiledRecordFolderChildrenVariance)
    {
        this.unfiledRecordFolderChildrenVariance = unfiledRecordFolderChildrenVariance;
        this.folderNumberStandardDeviation = (int)Math.floor(Math.sqrt(this.unfiledRecordFolderChildrenVariance));
    }

    public int getFolderNumberStandardDeviation()
    {
        return this.folderNumberStandardDeviation;
    }

    public boolean isCreateUnfiledRecordFolderStructure()
    {
        return createUnfiledRecordFolderStructure;
    }

    public void setCreateUnfiledRecordFolderStructure(boolean createUnfiledRecordFolderStructure)
    {
        this.createUnfiledRecordFolderStructure = createUnfiledRecordFolderStructure;
    }

    public int getRootUnfiledRecordFolderNumber()
    {
        return rootUnfiledRecordFolderNumber;
    }

    public void setRootUnfiledRecordFolderNumber(int rootUnfiledRecordFolderNumber)
    {
        this.rootUnfiledRecordFolderNumber = rootUnfiledRecordFolderNumber;
    }

    public long getLoadCheckDelay()
    {
        return loadCheckDelay;
    }

    public void setLoadCheckDelay(long loadCheckDelay)
    {
        this.loadCheckDelay = loadCheckDelay;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getEventNameLoadUnfiledRecordFolders()
    {
        return eventNameLoadUnfiledRecordFolders;
    }

    public String getEventNameScheduleLoaders()
    {
        return eventNameScheduleLoaders;
    }

    public String getEventNameLoadingComplete()
    {
        return eventNameLoadingComplete;
    }

    /**
     * Override the {@link #EVENT_NAME_LOAD_UNFILED_RECORD_FOLDERS default} output event name
     */
    public void setEventNameLoadUnfiledRecordFolders(String eventNameLoadUnfiledRecordFolders)
    {
        this.eventNameLoadUnfiledRecordFolders = eventNameLoadUnfiledRecordFolders;
    }

    /**
     * Override the {@link #EVENT_NAME_SCHEDULE_LOADERS default} output event name
     */
    public void setEventNameScheduleLoaders(String eventNameScheduleLoaders)
    {
        this.eventNameScheduleLoaders = eventNameScheduleLoaders;
    }

    /**
     * Override the {@link #EVENT_NAME_LOADING_COMPLETE default} output event name
     */
    public void setEventNameLoadingComplete(String eventNameLoadingComplete)
    {
        this.eventNameLoadingComplete = eventNameLoadingComplete;
    }

    @Override
    protected EventResult processEvent(Event arg0) throws Exception
    {
        // Are there still sessions active?
        long sessionCount = sessionService.getActiveSessionsCount();
        int loaderSessionsToCreate = maxActiveLoaders - (int) sessionCount;
        List<Event> nextEvents = new ArrayList<Event>(maxActiveLoaders);

        // Do we actually need to do anything
        if (!createUnfiledRecordFolderStructure)
        {
            return new EventResult("Unfiled Record Folders structure creation not wanted.", false);
        }
        if(unfiledRecordFolderDepth > 0)
        {
            //Load root unfiled record folders
            prepareRootUnfiledRecordFolders(loaderSessionsToCreate, nextEvents);
        }

        if(unfiledRecordFolderDepth > 1)
        {
            //Load unfiled record folder children
            prepareUnfiledRecordFolders(loaderSessionsToCreate, nextEvents);
        }
        // If there are no events, then we have finished
        String msg = null;
        if (loaderSessionsToCreate > 0 && nextEvents.size() == 0)
        {
            // There are no files or folders to load even though there are sessions available
            Event nextEvent = new Event(eventNameLoadingComplete, null);
            nextEvents.add(nextEvent);
            msg = "Loading completed.  Raising 'done' event.";
        }
        else
        {
            // Reschedule self
            Event nextEvent = new Event(eventNameScheduleLoaders, System.currentTimeMillis() + loadCheckDelay, null);
            nextEvents.add(nextEvent);
            msg = "Raised further " + (nextEvents.size() - 1) + " events and rescheduled self.";
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(msg);
        }

        EventResult result = new EventResult(msg, nextEvents);
        return result;
    }

    private void prepareRootUnfiledRecordFolders(int loaderSessionsToCreate, List<Event> nextEvents)
    {
        int skip = 0;
        int limit = 100;
        while (nextEvents.size() < loaderSessionsToCreate)
        {
            // Get categories needing loading
            List<FolderData> emptyFolders = fileFolderService.getFoldersByCounts(
                        UNFILED_CONTEXT,
                        Long.valueOf(UNFILED_RECORD_CONTAINER_LEVEL), Long.valueOf(UNFILED_RECORD_CONTAINER_LEVEL),
                        0L, Long.valueOf((rootUnfiledRecordFolderNumber - 1)),
                        null, null, // Ignore file limits
                        skip, limit);
            skip += limit;
            if (emptyFolders.size() == 0)
            {
                // The folders were populated in the mean time
                break;
            }
            // Schedule a load for each folder
            for (FolderData emptyFolder : emptyFolders)
            {
                int unfiledRecordFolderToCreate = rootUnfiledRecordFolderNumber - (int)emptyFolder.getFolderCount();
                try
                {
                    // Create a lock folder that has too many files and folders so that it won't be picked up
                    // by this process in subsequent trawls
                    String lockPath = emptyFolder.getPath() + "/locked";
                    FolderData lockFolder = new FolderData(
                            UUID.randomUUID().toString(),
                            emptyFolder.getContext(), lockPath,
                            Long.MAX_VALUE, Long.MAX_VALUE);
                    fileFolderService.createNewFolder(lockFolder);
                    // We locked this, so the load can be scheduled.
                    // The loader will remove the lock when it completes
                    DBObject loadData = BasicDBObjectBuilder.start()
                                .add(FIELD_CONTEXT, emptyFolder.getContext())
                                .add(FIELD_PATH, emptyFolder.getPath())
                                .add(FIELD_UNFILED_ROOT_FOLDERS_TO_CREATE, Integer.valueOf(unfiledRecordFolderToCreate))
                                .add(FIELD_UNFILED_FOLDERS_TO_CREATE, Integer.valueOf(0))
                                .add(FIELD_SITE_MANAGER, username)
                                .get();
                    Event loadEvent = new Event(eventNameLoadUnfiledRecordFolders, loadData);
                    // Each load event must be associated with a session
                    String sessionId = sessionService.startSession(loadData);
                    loadEvent.setSessionId(sessionId);
                    // Add the event to the list
                    nextEvents.add(loadEvent);
                }
                catch (Exception e)
                {
                    // The lock was already applied; find another
                    continue;
                }
                // Check if we have enough
                if (nextEvents.size() >= loaderSessionsToCreate)
                {
                    break;
                }
            }
        }
    }

    private void prepareUnfiledRecordFolders(int loaderSessionsToCreate, List<Event> nextEvents)
    {
        int skip = 0;
        int limit = 100;
        while (nextEvents.size() < loaderSessionsToCreate)
        {
            int maxChildren = unfiledRecordFolderChildrenAverage - folderNumberStandardDeviation - 1;
            // Get folders needing loading
            List<FolderData> emptyFolders = fileFolderService.getFoldersByCounts(
                    UNFILED_CONTEXT,
                    Long.valueOf(UNFILED_RECORD_CONTAINER_LEVEL+1), Long.valueOf(maxLevel-1),
                    0L, Long.valueOf(maxChildren),             // Get folders that still need folders
                    null, null,                                 // Ignore file limits
                    skip, limit);
            skip += limit;
            if (emptyFolders.size() == 0)
            {
                // The folders were populated in the mean time
                break;
            }
            // Schedule a load for each folder
            for (FolderData emptyFolder : emptyFolders)
            {
                int foldersToCreate = calculateRequiredFilePlanComponentNumber(unfiledRecordFolderChildrenAverage, folderNumberStandardDeviation) - (int) emptyFolder.getFolderCount();
                try
                {
                    // Create a lock folder that has too many files and folders so that it won't be picked up
                    // by this process in subsequent trawls
                    String lockPath = emptyFolder.getPath() + "/locked";
                    FolderData lockFolder = new FolderData(
                            UUID.randomUUID().toString(),
                            emptyFolder.getContext(), lockPath,
                            Long.MAX_VALUE, Long.MAX_VALUE);
                    fileFolderService.createNewFolder(lockFolder);
                    // We locked this, so the load can be scheduled.
                    // The loader will remove the lock when it completes
                    DBObject loadData = BasicDBObjectBuilder.start()
                            .add(FIELD_CONTEXT, emptyFolder.getContext())
                            .add(FIELD_PATH, emptyFolder.getPath())
                            .add(FIELD_UNFILED_ROOT_FOLDERS_TO_CREATE, Integer.valueOf(0))
                            .add(FIELD_UNFILED_FOLDERS_TO_CREATE, Integer.valueOf(foldersToCreate))
                            .add(FIELD_SITE_MANAGER, username)
                            .get();
                    Event loadEvent = new Event(eventNameLoadUnfiledRecordFolders, loadData);
                    // Each load event must be associated with a session
                    String sessionId = sessionService.startSession(loadData);
                    loadEvent.setSessionId(sessionId);
                    // Add the event to the list
                    nextEvents.add(loadEvent);
                }
                catch (Exception e)
                {
                    // The lock was already applied; find another
                    continue;
                }
                // Check if we have enough
                if (nextEvents.size() >= loaderSessionsToCreate)
                {
                    break;
                }
            }
        }
    }
}
