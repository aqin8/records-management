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
package org.alfresco.po.share;

import org.alfresco.po.share.site.document.FileDirectoryInfo;
import org.alfresco.webdrone.HtmlPage;
import org.alfresco.webdrone.WebDrone;
import org.alfresco.webdrone.exception.PageOperationException;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

/**
 * Extends {@link FileDirectoryInfo} to add RM specific methods
 *
 * @author Tuna Aksoy
 * @since 2.2
 */
public class RMFileDirectoryInfo extends FileDirectoryInfo
{

    public RMFileDirectoryInfo(String nodeRef, WebElement webElement,
            WebDrone drone)
    {
        super(nodeRef, webElement, drone);
    }

    /**
     * Action of selecting the declare records button
     * that appear in the action drop down, this is only
     * available in the Records Management Module.
     */
    public HtmlPage declareRecord()
    {
        selectMoreAction().click();
        try
        {
            drone.find(By.cssSelector("div#onActionSimpleRepoAction.rm-create-record a")).click();
        }
        catch (NoSuchElementException nse)
        {
            throw new PageOperationException("Unable to find declare record button from the drop down", nse);
        }
        canResume();
        return FactorySharePage.resolvePage(drone);
    }

    /**
     * Verify if item is a record.
     * Part of Records management module, when a document
     * is a record a small banner info is displayed indicating
     * that it is a record
     * @return true if record banner is visible
     */
    public boolean isRecord()
    {
        try
        {
            return this.find(By.cssSelector("div.info-banner")).isDisplayed();
        }
        catch (NoSuchElementException e) { }
        return false;
    }
}