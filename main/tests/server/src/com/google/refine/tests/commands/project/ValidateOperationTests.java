package com.google.refine.tests.commands.project;

import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.refine.ProjectManager;
import com.google.refine.importers.SeparatorBasedImporter;
import com.google.refine.model.medadata.DataPackageMetadata;
import com.google.refine.model.medadata.MetadataFactory;
import com.google.refine.model.medadata.MetadataFormat;
import com.google.refine.model.medadata.validator.ValidateOperation;
import com.google.refine.tests.importers.TsvCsvImporterTests;

import io.frictionlessdata.tableschema.Field;
import io.frictionlessdata.tableschema.exceptions.ForeignKeyException;
import io.frictionlessdata.tableschema.exceptions.PrimaryKeyException;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MetadataFactory.class)
public class ValidateOperationTests extends TsvCsvImporterTests  {
    
    private Logger logger = LoggerFactory.getLogger(ValidateOperationTests.class.getClass());
    
    private SeparatorBasedImporter parser = null;

    // variables
    private static String input;
    private DataPackageMetadata dataPackageMetadata;

    // mocks
    private ProjectManager projMan = null;
    
    private String optionsString;
    
    @BeforeClass
    public static void readData() throws IOException{
        //create input to test with
        input =  getFileContent("gdp.csv");
        // create an data type issue on the fly
         input = input.replace("28434203615.4795", "XXXXXXXXXXXXX");
//        String input = "Country Name,Country Code,Year,Value\n" + 
//                "Arab World,ARB,1968,25760683041.0826\n" + 
//                "China, CHN,1968,16289212000\n" + 
//                "Arab World,ARB,1969,28434203615.4795XXX\n";
    }
    
    @Before
    public void SetUp() throws JSONException, IOException, ValidationException, PrimaryKeyException, ForeignKeyException {
        super.setUp();
        
        optionsString = "{\"columnNames\": [\"Country Name\",\"Country Code\",\"Year\",\"Value\"]}";
        
        // mockup
        projMan = mock(ProjectManager.class);
        ProjectManager.singleton = projMan;
        
        dataPackageMetadata = new DataPackageMetadata();
        String content = getJSONContent("datapackage-sample.json");
        dataPackageMetadata.loadFromStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8.name())));
        
        // mock dependencies
        //given
        PowerMockito.mockStatic(MetadataFactory.class);
        BDDMockito.given(MetadataFactory.buildMetadata(MetadataFormat.DATAPACKAGE_METADATA)).willReturn(dataPackageMetadata);
        
        parser = new SeparatorBasedImporter();
        parseOneFile();
    }

    @After
    public void TearDown() {
        projMan = null;
        ProjectManager.singleton = null;
        project = null;
    }
    
    /**
     *  type or format error
     */
    @Test
    public void testTypeorFormatError()  {
        JSONObject optionObj = new JSONObject(optionsString);
        // run
        startValidateOperation(optionObj);
    }
    
    @Test
    public void testMinimumConstraint() {
        // options
        optionsString = "{\"columnNames\": [\"Year\"]}";
        JSONObject optionObj = new JSONObject(optionsString);
        
        // add Constraint
        String contraintKey = Field.CONSTRAINT_KEY_MINIMUM;
        String contraintValue = "1962";
        addConstraint(project.getSchema().getField("Year"), contraintKey,  contraintValue);
        
        // run
        startValidateOperation(optionObj);
    }
    
    @Test
    public void testMaximumConstraint() {
        // options
        optionsString = "{\"columnNames\": [\"Year\"]}";
        JSONObject optionObj = new JSONObject(optionsString);
        
        // add Constraint
        String contraintKey = Field.CONSTRAINT_KEY_MAXIMUM;
        String contraintValue = "2015";
        addConstraint(project.getSchema().getField("Year"), contraintKey,  contraintValue);
        
        // run
        startValidateOperation(optionObj);
    }
    
    @Test
    public void testPatternConstraint() {
        // options
        optionsString = "{\"columnNames\": [\"Year\"]}";
        JSONObject optionObj = new JSONObject(optionsString);
        
        // add Constraint
        String contraintKey = Field.CONSTRAINT_KEY_PATTERN;
        String contraintValue = "[0-9]{4}";
        addConstraint(project.getSchema().getField("Year"), contraintKey,  contraintValue);
        
        // run
        startValidateOperation(optionObj);
    }
    
    
    
    private void addConstraint(Field field, String contraintKey, Object contraintValue) {
        java.lang.reflect.Field f1;
        try {
            f1 = field.getClass().getDeclaredField("constraints");
            f1.setAccessible(true);
            Map<String, Object> existingMap = (HashMap<String, Object>)f1.get(field);
            if (existingMap == null) {
                existingMap = new HashMap<String, Object>();
            } 
            existingMap.put(contraintKey, contraintValue);
            f1.set(field, existingMap);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
     
    
    private void startValidateOperation(JSONObject options) {
        // SUT
        JSONObject report = new ValidateOperation(project, options).startProcess();
        
        System.out.println("validation report:" + report.toString(2));
    }
    
     private String getJSONContent(String fileName) throws IOException {
         InputStream in = this.getClass().getClassLoader()
                 .getResourceAsStream(fileName);
         String content = org.apache.commons.io.IOUtils.toString(in);
         
         return content;
     }
     

    private void parseOneFile() throws IOException{    
        String sep = ",";
         prepareOptions(sep, -1, 0, 0, 1, false, false);
         parseOneFile(parser, new StringReader(input));

         setDataPackageMetaData();
         
         Assert.assertEquals(project.columnModel.columns.size(), 4);
         Assert.assertNotNull(project.getSchema());
     }
     
     private void setDataPackageMetaData() {
         project.setMetadata(MetadataFormat.DATAPACKAGE_METADATA, dataPackageMetadata);
    }

    private static String getFileContent(String fileName) throws IOException {
         InputStream in = ValidateOperationTests.class.getClassLoader()
                 .getResourceAsStream(fileName);
         String content = org.apache.commons.io.IOUtils.toString(in);
         
         return content;
     }
}
