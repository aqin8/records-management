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
package org.alfresco.bm.dataload.rm.site;

import static org.alfresco.bm.data.DataCreationState.Created;
import static org.alfresco.bm.data.DataCreationState.Scheduled;
import static org.alfresco.bm.dataload.rm.role.RMRole.ADMINISTRATOR;
import static org.alfresco.bm.site.SiteVisibility.PUBLIC;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.restapi.RestAPIFactory;
import org.alfresco.bm.site.SiteData;
import org.alfresco.bm.site.SiteDataService;
import org.alfresco.bm.site.SiteMemberData;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Prepare event for RM Site creation
 *
 * @author Michael Suzuki
 * @author Derek Hulley
 * @author Tuna Aksoy
 * @since 1.0
 */
public class PrepareRMSite extends AbstractEventProcessor
{
    public static final String RM_SITE_DESC = "Records Management Site";
    public static final String RM_SITE_PRESET = "rm-site-dashboard";
    public static final String RM_SITE_ID = "rm";
    public static final String RM_SITE_TITLE = "Records Management";
    public static final String RM_SITE_DOMAIN = "default";
    public static final String RM_SITE_GUID = RM_SITE_ID;
    public static final String RM_SITE_TYPE = "{http://www.alfresco.org/model/recordsmanagement/1.0}rmsite";
    public static final String RM_SITE_VISIBILITY = PUBLIC.toString();

    public static final String FIELD_SITE_ID = "siteId";
    public static final String FIELD_SITE_MANAGER = "siteManager";
    public static final String FIELD_ONLY_DB_LOAD = "onlyDbLoad";

    public static final String DEFAULT_EVENT_NAME_RM_SITE_PREPARED = "rmSitePrepared";

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private SiteDataService siteDataService;

    @Autowired
    private RestAPIFactory restAPIFactory;

    private boolean createRMSite = false;
    private String eventNameRMSitePrepared = DEFAULT_EVENT_NAME_RM_SITE_PREPARED;
    private String eventNameLoadRMSiteIntoDB = "loadRMSiteIntoDB";
    private String eventNameContinueLoadingData = "scheduleFilePlanLoaders";
    private String username;
    private String password;

    /**
     * @return the createRMSite
     */
    public boolean isCreateRMSite()
    {
        return this.createRMSite;
    }

    /**
     * @param createRMSite the createRMSite to set
     */
    public void setCreateRMSite(boolean createRMSite)
    {
        this.createRMSite = createRMSite;
    }

    /**
     * @return the eventNameRMSitePrepared
     */
    public String getEventNameRMSitePrepared()
    {
        return this.eventNameRMSitePrepared;
    }

    /**
     * Override the {@link #DEFAULT_EVENT_NAME_RM_SITE_PREPARED default} event name when sites have been created.
     */
    public void setEventNameRMSitePrepared(String eventNameRMSitePrepared)
    {
        this.eventNameRMSitePrepared = eventNameRMSitePrepared;
    }

    public String getEventNameLoadRMSiteIntoDB()
    {
        return eventNameLoadRMSiteIntoDB;
    }

    public void setEventNameLoadRMSiteIntoDB(String eventNameLoadRMSiteIntoDB)
    {
        this.eventNameLoadRMSiteIntoDB = eventNameLoadRMSiteIntoDB;
    }

    public String getEventNameContinueLoadingData()
    {
        return eventNameContinueLoadingData;
    }

    public void setEventNameContinueLoadingData(String eventNameContinueLoadingData)
    {
        this.eventNameContinueLoadingData = eventNameContinueLoadingData;
    }

    /**
     * @return the username
     */
    public String getUsername()
    {
        return this.username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username)
    {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword()
    {
        return this.password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password)
    {
        this.password = password;
    }

    /**
     * @see org.alfresco.bm.event.AbstractEventProcessor#processEvent(org.alfresco.bm.event.Event)
     */
    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        StringBuilder msg = new StringBuilder("Preparing Records Management: \n");
        List<Event> events = new ArrayList<Event>(10);

        UserData rmAdmin = userDataService.findUserByUsername(getUsername());
        if (rmAdmin == null)
        {
            rmAdmin = new UserData();
            rmAdmin.setCreationState(Created);
            rmAdmin.setDomain(RM_SITE_DOMAIN);
            rmAdmin.setUsername(getUsername());
            rmAdmin.setPassword(getPassword());
            userDataService.createNewUser(rmAdmin);
        }
        else
        {
            // Check for creation
            if (rmAdmin.getCreationState() != Created)
            {
                userDataService.setUserCreationState(getUsername(), Created);
                msg.append("   Updating user " + getUsername() + " state to created.\n");
            }
        }

        boolean existsRMSite = restAPIFactory.getRMSiteAPI(getUsername()).existsRMSite();

        //rm site does not exists and we don't want to create it
        if (!existsRMSite && !isCreateRMSite())
        {
            return new EventResult("RM Site creation not wanted.", false);
        }

        SiteData rmSite = siteDataService.getSite(RM_SITE_ID);
        if(rmSite == null)
        {
            // Create data
            rmSite = new SiteData();
            rmSite.setSiteId(RM_SITE_ID);
            rmSite.setTitle(RM_SITE_TITLE);
            rmSite.setGuid(RM_SITE_GUID);
            rmSite.setDomain(RM_SITE_DOMAIN);
            rmSite.setDescription(RM_SITE_DESC);
            rmSite.setSitePreset(RM_SITE_PRESET);
            rmSite.setVisibility(RM_SITE_VISIBILITY);
            rmSite.setType(RM_SITE_TYPE);
            rmSite.setCreationState(Scheduled);
            siteDataService.addSite(rmSite);
            msg.append("   Added RM site '" + RM_SITE_ID + "' as created.\n");

            // Record the administrator
            SiteMemberData rmAdminMember = new SiteMemberData();
            rmAdminMember.setCreationState(Created);
            rmAdminMember.setRole(ADMINISTRATOR.toString());
            rmAdminMember.setSiteId(RM_SITE_ID);
            rmAdminMember.setUsername(getUsername());
            siteDataService.addSiteMember(rmAdminMember);
            msg.append("   Added user '" + getUsername() + "' RM administrator.\n");
        }

        BasicDBObjectBuilder builder = BasicDBObjectBuilder.start();
        builder.add(FIELD_SITE_ID, rmSite.getSiteId())
               .add(FIELD_SITE_MANAGER, getUsername());

        //rm site exists and it is loaded in mongo db
        if(existsRMSite && rmSite.getCreationState() == Created)
        {
            return new EventResult("RM Site already created, continue loading data.", new Event(getEventNameContinueLoadingData(), null));
        }

        //rm sites exists and it is not loaded in mongo db
        if(existsRMSite && rmSite.getCreationState() != Created)
        {
            builder.add(FIELD_ONLY_DB_LOAD, true);
            DBObject data = builder.get();
            events.add(new Event(getEventNameLoadRMSiteIntoDB(), data));
        }

        //rm sites does not exists and will be created
        if(!existsRMSite)
        {
            DBObject data = builder.get();
            events.add(new Event(getEventNameRMSitePrepared(), data));
        }

        // Done
        return new EventResult(msg.toString(), events);
    }
}
