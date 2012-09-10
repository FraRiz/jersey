/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.server.wadl.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import javax.inject.Singleton;
import javax.xml.bind.Marshaller;

import org.glassfish.jersey.server.wadl.WadlApplicationContext;

import com.sun.research.ws.wadl.Application;

/**
 *
 * @author Paul Sandoz (paul.sandoz at oracle.com)
 */
@Singleton
@Path("application.wadl")
public final class WadlResource {

    public static final String HTTPDATEFORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final Logger LOGGER = Logger.getLogger(WadlResource.class.getName());

    private URI lastBaseUri;
    private byte[] wadlXmlRepresentation;
    private String lastModified;

    public WadlResource() {
        this.lastModified = new SimpleDateFormat(HTTPDATEFORMAT).format(new Date());
    }

    @Produces({"application/vnd.sun.wadl+xml", "application/xml"})
    @GET
    public synchronized Response getWadl(@Context UriInfo uriInfo, @Context WadlApplicationContext wadlContext) {
        if(!wadlContext.isWadlGenerationEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if ((wadlXmlRepresentation == null) || ((lastBaseUri != null) && !lastBaseUri.equals(uriInfo.getBaseUri())) ) {
            this.lastBaseUri = uriInfo.getBaseUri();
            this.lastModified = new SimpleDateFormat(HTTPDATEFORMAT).format(new Date());

            ApplicationDescription applicationDescription =
                    wadlContext.getApplication(uriInfo);
            Application application = applicationDescription.getApplication();

            try {
                final Marshaller marshaller = wadlContext.getJAXBContext().createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                marshaller.marshal(application, os);
                wadlXmlRepresentation = os.toByteArray();
                os.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not marshal wadl Application.", e);
                return Response.ok(applicationDescription).build();
            }
        }

        return Response.ok(new ByteArrayInputStream(wadlXmlRepresentation)).header("Last-modified", lastModified).build();
    }

    @Produces({"application/xml"})
    @GET
    @Path("{path}")
    public synchronized Response geExternalGrammar(
            @Context UriInfo uriInfo,
            @Context WadlApplicationContext wadlContext,
            @PathParam("path") String path) {

        // Fail if wadl generation is disabled
        if(!wadlContext.isWadlGenerationEnabled()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ApplicationDescription applicationDescription =
                wadlContext.getApplication(uriInfo);

        // Fail is we don't have any metadata for this path
        ApplicationDescription.ExternalGrammar externalMetadata = applicationDescription.getExternalGrammar( path );

        if( externalMetadata==null ) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Return the data
        return Response.ok().type( externalMetadata.getType() )
                .entity(externalMetadata.getContent())
                .build();
    }
}