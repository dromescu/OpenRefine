package com.google.refine.model.medadata.validator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.model.Project;
import com.google.refine.model.medadata.validator.checks.TypeorFormatError;
import com.google.refine.model.medadata.validator.checks.Validator;
import com.google.refine.util.JSONUtilities;

import io.frictionlessdata.tableschema.Field;

public class ValidatorInspector {
    final static Logger logger = LoggerFactory.getLogger(ValidatorInspector.class);
    
    public static String CONSTRAINT_KEY = "constraint";
    public static String CONSTRAINT_KEY_EXTRA = "constraint-extra";
    public static String CONSTRAINT_TYPE_KEY = "type";
    public static String CONSTRAINT_FORMAT_KEY = "format";
    
    /**
     * Return a report contains the validate result
     * @param project
     * @param options
     * @return
     */
    static JSONObject inspect(Project project, JSONObject options) {
        List<String> columnNames;
        String COLUMN_NAMES_KEY = "columnNames";
        Map<String, List<Validator>> columnToCheckersMap = new HashMap<String, List<Validator>>();
        JSONArray validateReport = new JSONArray();
        
        logger.info("starting inspect with options:" + options.toString());
        columnNames = JSONUtilities.toStringList(options.getJSONArray(COLUMN_NAMES_KEY));
        
        // build the check items
        List<Validator> validatorList = null;
        for(String columnName : columnNames) {
            validatorList = compileChecks(project, columnName, options);
            if (validatorList.size() >= 0)
                columnToCheckersMap.put(columnName, validatorList);
        }
        logger.info("==========================================================");
        logger.info("Inspector finished the checks compile. will do following check:");
        for (Entry<String, List<Validator>> entry : columnToCheckersMap.entrySet()) {
            logger.info("Column Name: " + entry.getKey());
            for (Validator v : entry.getValue()) {
                logger.info("\t Validator: " + v.getClass().getSimpleName());
            }
        }
        logger.info("==========================================================");

        // do the inspect in another loop:
        for(String columnName : columnNames) {
            List<Validator> validators = columnToCheckersMap.get(columnName);
            if (validators != null) {
                for (Validator validator : validators) {
                    JSONArray result = validator.validate();
                    if (result != null && result.length() > 0)
                        JSONUtilities.concatArray(validateReport, result);
                }
            }
        }
        logger.info("Inspector finished the validation.");
        
        return new JSONObject().put("validation-reports", (Object)validateReport);
    }

    private static List<Validator> compileChecks(Project project, String columnName, JSONObject options) {
        Map<String, Class> constraintHandlersMap = ValidatorRegistry.getInstance().getConstraintHandlersMap();
        
        if (project.getSchema() == null) {
            logger.error("Cannot find the schema defined, failed to do compileChecks:");
            return null;
        }
        
        Field field = project.getSchema().getField(columnName);
        List<Validator> validatorList = new ArrayList<Validator>();
        
        int columnIndex = project.columnModel.getColumnIndexByName(columnName);
        
        options.put(CONSTRAINT_KEY, new JSONObject().put(CONSTRAINT_TYPE_KEY, field.getType()));
        // for TypeofFormatError, grab the format information
        options.put(CONSTRAINT_KEY_EXTRA, new JSONObject().put(CONSTRAINT_FORMAT_KEY, field.getFormat()));
        validatorList.add(new TypeorFormatError(project, columnIndex, options));
        
        
        if (field.getConstraints() != null) {
            for (Entry<String, Object> entry : field.getConstraints().entrySet()) {
                Class<Validator> clazz = constraintHandlersMap.get(entry.getKey());
                try {
                    options.put(CONSTRAINT_KEY, new JSONObject().put(entry.getKey(), entry.getValue()));
                    Constructor<Validator> c = clazz.getConstructor(Project.class, String.class, JSONObject.class);
                    validatorList.add(c.newInstance(project, columnIndex, options));
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    logger.error("failed to do compileChecks:" + ExceptionUtils.getStackTrace(e));
                }
            }
        }
        
        return validatorList;
    }
    
    
}
