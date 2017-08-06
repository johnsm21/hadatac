package org.hadatac.entity.pojo;

import org.hadatac.metadata.loader.ValueCellProcessing;

public class HADatAcThing {
	
    String uri;
    String type;
    String label;
    String comment;

    public String getUri() {
    	return uri.replace("<","").replace(">","");
    }

    public String getUriNamespace() {
	return ValueCellProcessing.replaceNameSpaceEx(uri.replace("<","").replace(">",""));
    }

    public void setUri(String uri) {
	this.uri = uri;
    }
    
    public String getType() {
	return type;
    }
    
    public String getTypeNamespace() {
	return ValueCellProcessing.replaceNameSpaceEx(uri.replace("<","").replace(">",""));
    }

    public void setType(String type) {
	this.type = type;
    }	
    
    public String getLabel() {
	return label;
    }
    
    public void setLabel(String label) {
	this.label = label;
    }	
    
    public String getComment() {
	return comment;
    }
    
    public void setComment(String comment) {
	this.comment = comment;
    }	
    
}
