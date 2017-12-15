package com.google.refine.model.medadata.validator.checks;

import org.json.JSONObject;

import com.google.refine.model.Cell;
import com.google.refine.model.Project;

import io.frictionlessdata.tableschema.Field;
import io.frictionlessdata.tableschema.exceptions.ConstraintsException;
import io.frictionlessdata.tableschema.exceptions.InvalidCastException;

@SuppressWarnings("rawtypes")
public class MinimumConstraint extends AbstractValidator {
private Comparable threashold;
    
    public MinimumConstraint(Project project, int cellIndex, JSONObject options) throws InvalidCastException, ConstraintsException {
        super(project, cellIndex, options);
        this.code = "minimum-constraint";
        threashold = (Comparable)field.getConstraints()
                .get(Field.CONSTRAINT_KEY_MINIMUM);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean checkCell(Cell cell) {
        boolean valid = true;
        
        try {
            Comparable value = field.castValue(cell.value.toString());
            // return this - threashold
            if (value.compareTo(threashold) < 0)
                valid = false;
        } catch (InvalidCastException | ConstraintsException e) {
                valid = false;
        } 
            
        return valid;
    }
}