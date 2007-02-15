/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.portal.charon.handlers;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.portal.util.ErrorReporter;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.ToolException;

/**
 * @author ieb
 *
 */
public class ErrorDoneHandler extends BasePortalHandler
{
	
	public ErrorDoneHandler() {
		urlFragment = "error-reported";
	}
	@Override
	public int doPost( String[] parts, HttpServletRequest req, HttpServletResponse res, Session session) throws PortalHandlerException {
		return doGet(parts, req, res, session);
	}


	@Override
	public int doGet( String[] parts, HttpServletRequest req, HttpServletResponse res, Session session) throws PortalHandlerException
	{
		if ((parts.length >= 2) && (parts[1].equals("error-reported")))
		{
			try {
				doErrorDone(req, res);
				return END;
			} catch ( Exception ex ) {
				throw new PortalHandlerException(ex);
			}
		} else {
			return NEXT;
		}
	}
	public void doErrorDone(HttpServletRequest req, HttpServletResponse res) throws ToolException, IOException
	{
		portal.setupForward(req, res, null, null);

		ErrorReporter err = new ErrorReporter();
		err.thanksResponse(req, res);
	}

}
