/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.commands.project;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.everit.json.schema.ValidationException;
import org.json.JSONException;

import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.model.medadata.DataPackageMetadata;
import com.google.refine.model.medadata.IMetadata;
import com.google.refine.model.medadata.MetadataFactory;
import com.google.refine.model.medadata.MetadataFormat;
import com.google.refine.model.medadata.SchemaExtension;

import io.frictionlessdata.datapackage.Resource;
import io.frictionlessdata.datapackage.exceptions.DataPackageException;

public class GetMetadataCommand extends Command {
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        try {
            Project project;
            MetadataFormat metadataFormat;
            try {
                project = getProject(request);
                metadataFormat = MetadataFormat.valueOf(request.getParameter("metadataFormat"));
            } catch (ServletException e) {
                respond(response, "error", e.getLocalizedMessage());
                return;
            }
            
            IMetadata metadata = project.getMetadata(metadataFormat);
            if (metadata == null) {
                metadata = MetadataFactory.buildMetadata(metadataFormat);
                if (metadataFormat == MetadataFormat.DATAPACKAGE_METADATA) {
                    DataPackageMetadata dpm = (DataPackageMetadata)metadata;
                    Resource resource = SchemaExtension.createResource(project.getProjectMetadata().getName(),
                            project.columnModel);
                    dpm.getPackage().addResource(resource);
                }
            }
            respondJSONObject(response, metadata.getJSON());
        } catch (JSONException e) {
            respondException(response, e);
        } catch (ValidationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (DataPackageException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
