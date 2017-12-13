package com.google.refine.model.medadata.validator.checks;

import org.json.JSONObject;

import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.medadata.validator.ValidatorInspector;
import com.google.refine.model.medadata.validator.ValidatorRegistry;

public class MinimumLengthConstraint extends AbstractValidator {
    private int minLength;
    
    public MinimumLengthConstraint(Project project, int cellIndex, JSONObject options) {
        super(project, cellIndex, options);
        this.code = "minimum-length-constrain";
        
        minLength = options.getJSONObject(ValidatorInspector.CONSTRAINT_KEY)
                .getInt(ValidatorRegistry.CONSTRAINT_MINLENGTH);
    }
    
    @Override
    public boolean filter(Cell cell) {
        return true;
    }
    
    @Override
    public boolean checkCell(Cell cell) {
        if (cell == null || cell.value == null)
            return false;
        
        return cell.value.toString().length() >= minLength;
    }
    
    
}