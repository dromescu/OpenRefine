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

package com.google.refine.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.RefineServlet;
import com.google.refine.history.History;
import com.google.refine.model.changes.ColumnAdditionChange;
import com.google.refine.model.changes.ColumnChange;
import com.google.refine.model.changes.ColumnMoveChange;
import com.google.refine.model.changes.ColumnRemovalChange;
import com.google.refine.model.changes.ColumnReorderChange;
import com.google.refine.model.changes.ColumnSplitChange;
import com.google.refine.model.medadata.DataPackageMetadata;
import com.google.refine.model.medadata.IMetadata;
import com.google.refine.model.medadata.MetadataFactory;
import com.google.refine.model.medadata.MetadataFormat;
import com.google.refine.model.medadata.ProjectMetadata;
import com.google.refine.model.medadata.SchemaExtension;
import com.google.refine.process.ProcessManager;
import com.google.refine.util.ParsingUtilities;
import com.google.refine.util.Pool;

import io.frictionlessdata.datapackage.Resource;
import io.frictionlessdata.tableschema.Field;
import io.frictionlessdata.tableschema.Schema;
import io.frictionlessdata.tableschema.exceptions.ForeignKeyException;
import io.frictionlessdata.tableschema.exceptions.PrimaryKeyException;

public class Project {
    final static protected Map<String, Class<? extends OverlayModel>> 
        s_overlayModelClasses = new HashMap<String, Class<? extends OverlayModel>>();
    
    final public long                       id;
    final public List<Row>                  rows = new ArrayList<Row>();
    final public ColumnModel                columnModel = new ColumnModel();
    final public RecordModel                recordModel = new RecordModel();
    final public Map<String, OverlayModel>  overlayModels = new HashMap<String, OverlayModel>();
    final public History                    history;
    
    transient public ProcessManager processManager = new ProcessManager();
    transient private LocalDateTime _lastSave = LocalDateTime.now();

    final static Logger logger = LoggerFactory.getLogger("project");
    
    private Map<MetadataFormat, IMetadata> metadataMap;
    
    private Schema schema;
    
    static public long generateID() {
        return System.currentTimeMillis() + Math.round(Math.random() * 1000000000000L);
    }

    public Project() {
        this(generateID());
    }

    protected Project(long id) {
        this.id = id;
        this.history = new History(this);
        
        metadataMap = new HashMap<MetadataFormat, IMetadata>();
        if (ProjectManager.singleton != null)
            metadataMap.put(MetadataFormat.PROJECT_METADATA, ProjectManager.singleton.getProjectMetadata(id));
    }
    
    static public void registerOverlayModel(String modelName, Class<? extends OverlayModel> klass) {
        s_overlayModelClasses.put(modelName, klass);
    }
    
    /**
     * Free/dispose of project data from memory.
     */
    public void dispose() {
        for (OverlayModel overlayModel : overlayModels.values()) {
            try {
                overlayModel.dispose(this);
            } catch (Exception e) {
                logger.warn("Error signaling overlay model before disposing", e);
            }
        }
        ProjectManager.singleton.getInterProjectModel().flushJoinsInvolvingProject(this.id);
        // The rest of the project should get garbage collected when we return.
    }

    public LocalDateTime getLastSave(){
        return this._lastSave;
    }
    /**
     * Sets the lastSave time to now
     */
    public void setLastSave(){
        this._lastSave = LocalDateTime.now();
    }

    public void saveToOutputStream(OutputStream out, Pool pool) throws IOException {
        for (OverlayModel overlayModel : overlayModels.values()) {
            try {
                overlayModel.onBeforeSave(this);
            } catch (Exception e) {
                logger.warn("Error signaling overlay model before saving", e);
            }
        }
        
        Writer writer = new OutputStreamWriter(out, "UTF-8");
        try {
            Properties options = new Properties();
            options.setProperty("mode", "save");
            options.put("pool", pool);

            saveToWriter(writer, options);
        } finally {
            writer.flush();
        }
        
        for (OverlayModel overlayModel : overlayModels.values()) {
            try {
                overlayModel.onAfterSave(this);
            } catch (Exception e) {
                logger.warn("Error signaling overlay model after saving", e);
            }
        }
    }

    protected void saveToWriter(Writer writer, Properties options) throws IOException {
        writer.write(RefineServlet.VERSION); writer.write('\n');
        
        writer.write("columnModel=\n"); columnModel.save(writer, options);
        writer.write("history=\n"); history.save(writer, options);
        
        for (String modelName : overlayModels.keySet()) {
            writer.write("overlayModel:");
            writer.write(modelName);
            writer.write("=");
            
            try {
                JSONWriter jsonWriter = new JSONWriter(writer);
                
                overlayModels.get(modelName).write(jsonWriter, options);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            writer.write('\n');
        }
        
        writer.write("rowCount="); writer.write(Integer.toString(rows.size())); writer.write('\n');
        for (Row row : rows) {
            row.save(writer, options); writer.write('\n');
        }
    }
    
    static public Project loadFromInputStream(InputStream is, long id, Pool pool) throws Exception {
        return loadFromReader(new LineNumberReader(new InputStreamReader(is, "UTF-8")), id, pool);
    }
    
    static private Project loadFromReader(
        LineNumberReader reader,
        long id,
        Pool pool
    ) throws Exception {
        long start = System.currentTimeMillis();
        
        // version of Refine which wrote the file
        /* String version = */ reader.readLine();
        
        Project project = new Project(id);
        int maxCellCount = 0;
        
        String line;
        while ((line = reader.readLine()) != null) {
            int equal = line.indexOf('=');
            String field = line.substring(0, equal);
            String value = line.substring(equal + 1);
            
            // backward compatibility
            if ("protograph".equals(field)) {
                field = "overlayModel:freebaseProtograph";
            }
            
            if ("columnModel".equals(field)) {
                project.columnModel.load(reader);
            } else if ("history".equals(field)) {
                project.history.load(project, reader);
            } else if ("rowCount".equals(field)) {
                int count = Integer.parseInt(value);

                for (int i = 0; i < count; i++) {
                    line = reader.readLine();
                    if (line != null) {
                        Row row = Row.load(line, pool);
                        project.rows.add(row);
                        maxCellCount = Math.max(maxCellCount, row.cells.size());
                    }
                }
            } else if (field.startsWith("overlayModel:")) {
                String modelName = field.substring("overlayModel:".length());
                if (s_overlayModelClasses.containsKey(modelName)) {
                    Class<? extends OverlayModel> klass = s_overlayModelClasses.get(modelName);
                    
                    try {
                        Method loadMethod = klass.getMethod("load", Project.class, JSONObject.class);
                        JSONObject obj = ParsingUtilities.evaluateJsonStringToObject(value);
                    
                        OverlayModel overlayModel = (OverlayModel) loadMethod.invoke(null, project, obj);
                        
                        project.overlayModels.put(modelName, overlayModel);
                    } catch (Exception e) {
                        logger.error("Failed to load overlay model " + modelName);
                    }
                }
            }
        }

        project.columnModel.setMaxCellIndex(maxCellCount - 1);

        logger.info(
            "Loaded project {} from disk in {} sec(s)",id,Long.toString((System.currentTimeMillis() - start) / 1000)
        );

        project.update();

        return project;
    }

    public void update() {
        columnModel.update();
        recordModel.update(this);
    }
    
    public void updateColumnChange(ColumnChange change) {
        JSONObject schemaObj = null;
        Schema schema = null;
        
        this.update();
        // update the data package metadata
        DataPackageMetadata dp = getDataPackageMetadata();
        if (dp == null) {
            dp = (DataPackageMetadata) MetadataFactory.buildMetadata(MetadataFormat.DATAPACKAGE_METADATA);
            setMetadata(MetadataFormat.DATAPACKAGE_METADATA, dp);
        }
        
        try {
             if (dp.getPackage().getResources().size() > 0) {
                 schemaObj = dp.getPackage().getResources().get(0).getSchema();
                 schema = new Schema(schemaObj);
             } else {
                 Resource resource = SchemaExtension.createResource(getProjectMetadata().getName(),
                         columnModel);
                 dp.getPackage().addResource(resource);
                 schema = new Schema();
             }
        } catch (Exception e) {
            logger.error(ExceptionUtils.getStackTrace(e));
        }
        
        String changeClazzName = change.getClass().getSimpleName();
        switch (changeClazzName) {
            case "ColumnAdditionChange":
                ColumnAdditionChange cac = (ColumnAdditionChange)change;
                Field field = new Field(cac.getColumnName(), 
                        Field.FIELD_TYPE_STRING);
                SchemaExtension.insertField(schema, field, cac.getColumnIndex());
                break;
            case "ColumnRemovalChange":
                ColumnRemovalChange crc = (ColumnRemovalChange)change;
                SchemaExtension.removeField(schema, crc.getOldColumnIndex());
                break;
            case "ColumnMoveChange":
                ColumnMoveChange cmc = (ColumnMoveChange)change;
                Field f = SchemaExtension.removeField(schema, cmc.getOldColumnIndex());
                SchemaExtension.insertField(schema, f, cmc.getNewColumnIndex());
                break;
            case "ColumnReorderChange":
                ColumnReorderChange crec = (ColumnReorderChange)change;
                Schema newSchema = new Schema();
                List<String> columnNames = crec.getColumnNames();
                for (String columnName : columnNames) {
                    newSchema.addField(new Field(columnName, 
                        Field.FIELD_TYPE_STRING));
                }
                schema = newSchema;
                break;
            case "ColumnSplitChange":
                ColumnSplitChange csc = (ColumnSplitChange)change;
                int baseIndex = csc.getColumnIndex();
                for (String columnName : csc.getColumnNames()) {
                    SchemaExtension.insertField(schema,
                            new Field(columnName, Field.FIELD_TYPE_STRING),
                            baseIndex++);
                }
                if (csc.isRemoveOriginalColumn()) {
                    SchemaExtension.removeField(schema, csc.getColumnIndex());
                }
                break;
             default:
                 logger.warn("Unhandled column change:" + changeClazzName);
                 break;
        }
        
        JSONObject resourceJSON = dp.getPackage().getResources().get(0).getJson();
        resourceJSON.put(Resource.JSON_KEY_SCHEMA, schema.getJson());
    }


    //wrapper of processManager variable to allow unit testing
    //TODO make the processManager variable private, and force all calls through this method
    public ProcessManager getProcessManager() {
        return this.processManager;
    }
    
    /**
     * ProjectMetadata should always be there.
     * @return ProjectMetadata
     */
    public ProjectMetadata getProjectMetadata() {
        return (ProjectMetadata) getMetadata(MetadataFormat.PROJECT_METADATA);
    }
    
    public DataPackageMetadata getDataPackageMetadata() {
        return (DataPackageMetadata) getMetadata(MetadataFormat.DATAPACKAGE_METADATA);
    }
    
    public Schema getSchema() {
        return schema;
    }
    
    public IMetadata getMetadata(MetadataFormat format) {
        return metadataMap.get(format);
    }
    
    public Map<MetadataFormat, IMetadata> getMetadataMap() {
        return metadataMap;
    }
    
    public void setMetadata(MetadataFormat format, IMetadata metadata) {
        getMetadataMap().put(format, metadata);
        // inject the schema as a shorthand for access and reuse
        if (format == MetadataFormat.DATAPACKAGE_METADATA) {
            if (getDataPackageMetadata() != null
                    && getDataPackageMetadata().getPackage().getResources().size() > 0) {
                JSONObject jsonSchema = getDataPackageMetadata().getPackage().getResources().get(0).getSchema();
                if (jsonSchema != null) {
                    try {
                        schema = new Schema(jsonSchema, true);
                    } catch (ValidationException | PrimaryKeyException | ForeignKeyException e) {
                        logger.error("extract schema failed:" + ExceptionUtils.getFullStackTrace(e));
                    }
                }
                // XXX: metadata:: infer if not exist
            }
        }
    }
}
