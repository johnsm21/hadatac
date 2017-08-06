package org.hadatac.console.controllers.annotator;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.net.URLEncoder;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.hadatac.utils.Collections;
import org.hadatac.utils.Feedback;
import org.hadatac.utils.ConfigProp;
import org.hadatac.utils.State;
import org.hadatac.console.views.html.annotator.*;
import org.hadatac.console.models.AssignOptionForm;
import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.controllers.annotator.FileProcessing;
import org.hadatac.console.controllers.annotator.routes;
import org.hadatac.console.models.SysUser;
import org.hadatac.entity.pojo.DataAcquisition;
import org.hadatac.entity.pojo.DataFile;
import org.hadatac.entity.pojo.Deployment;
import org.hadatac.entity.pojo.DataAcquisitionSchema;
import org.hadatac.entity.pojo.DataAcquisitionSchemaAttribute;
import org.hadatac.entity.pojo.Study;
import org.hadatac.entity.pojo.TriggeringEvent;
import org.hadatac.entity.pojo.User;
import org.hadatac.metadata.loader.ValueCellProcessing;
import org.labkey.remoteapi.CommandException;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import play.Play;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.*;
import play.mvc.Result;
import play.twirl.api.Html;

public class PrepareIngestion extends Controller {

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result create(String file_name, String da_uri) {

    	if (session().get("LabKeyUserName") == null && session().get("LabKeyPassword") == null) {
    		return redirect(org.hadatac.console.controllers.triplestore.routes.LoadKB.logInLabkey(
			        routes.PrepareIngestion.create(file_name,da_uri).url()));
    	}
    		
	final String kbPrefix = Play.application().configuration().getString("hadatac.community.ont_prefix") + "-kb:";

	String path = "";
	String labels = "";
	String ownerEmail = "";
	DataAcquisition da = null;
	DataFile file = null;

	try {
	    file_name = URLEncoder.encode(file_name, "UTF-8");
	} catch (Exception e) {
	    System.out.println("[ERROR] encoding file name");
	}
	    
	//System.out.println("file <" + file_name + ">");

	ownerEmail = AuthApplication.getLocalUser(session()).getEmail();
	file = DataFile.findByName(ownerEmail, file_name);
	if (file == null) {
	    return ok(prepareIngestion.render(file_name, da, "[ERROR] Could not update file records with new DA information"));
	}

	// Load associated DA
	if (da_uri != null && !da_uri.equals("")) {
	    da = DataAcquisition.findByUri(ValueCellProcessing.replacePrefixEx(da_uri));

	    if (da != null) {
		return ok(prepareIngestion.render(file_name, da, "DA associated with file has been retrieved"));
	    } else {
		String message = "[ERROR] Could not load assigned DA from DA's URI : " + da_uri;
		return badRequest(message);
	    }
	}

        // OR create a new DA if the file is not associated with any existing DA

        String da_label = "";
	String new_da_uri = "";

	if (!file_name.startsWith("DA-")) {
	    da_label = "DA-" + file_name;
	} else {
	    da_label = file_name;
	}
	da_label = da_label.replace(".csv","").replace(".","").replace("+","-");
	new_da_uri = kbPrefix + da_label;

	da = new DataAcquisition();
	da.setTriggeringEvent(TriggeringEvent.INITIAL_DEPLOYMENT);
	da.setLabel(da_label);
	da.setUri(ValueCellProcessing.replacePrefixEx(new_da_uri));

	SysUser user = SysUser.findByEmail(ownerEmail);
	if (user == null) {
	    System.out.println("The specified owner email " + ownerEmail + " is not a valid user!");
	} else {
	    da.setOwnerUri(user.getUri());
	    da.setPermissionUri(user.getUri());
	}
    	
	da.save();
	
	// save DA
	try {
	    da.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
	} catch (CommandException e) {
	    //System.out.println("[Warning] Creating new Data Acquisition: error from PrepareIngestion's saveToLabKey()");
	}

	file.setDataAcquisitionUri(da.getUri());
	file.save();

	return ok(prepareIngestion.render(file_name, da, "New data acquisition has been created to support file ingestion"));
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result postCreate(String file_name, String da_uri) {
	return create(file_name, da_uri);
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result refine(String file_name, String da_uri, String message) {

	DataAcquisition da = null;

	// Load associated DA
	if (da_uri != null && !da_uri.equals("")) {
	    da = DataAcquisition.findByUri(da_uri);
	    if (da != null) {
		return ok(prepareIngestion.render(file_name, da, message));
	    } else {
		System.out.println("[ERROR] Could not load assigned DA from DA's URI");
	    }
	}
	return badRequest("[ERROR] In PrepareIngestion.refine, cannot retrieve DA from provided URI");
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result postRefine(String file_name, String da_uri, String message) {
	return refine(file_name, da_uri, message);
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result selectStudy(String file_name, String da_uri) {

	List<Study> studies = Study.find();
	
	return ok(selectStudy.render(file_name, da_uri, studies));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result selectDeployment(String file_name, String da_uri) {

	State active = new State(State.ACTIVE);

	List<Deployment> deployments = Deployment.find(active);
	
	return ok(selectDeployment.render(file_name, da_uri, deployments));
    }

    public static Result selectSchema(String file_name, String da_uri) {

	List<DataAcquisitionSchema> schemas = DataAcquisitionSchema.findAll();
	
	return ok(selectSchema.render(file_name, da_uri, schemas));
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result processSelectStudy(String file_name, String da_uri) {
	Form<AssignOptionForm> form = Form.form(AssignOptionForm.class).bindFromRequest();
	String message = "";
        AssignOptionForm data = form.get();
	String std_uri = data.getOption();
	//System.out.println("Showing returned option: " + std_uri);

	if (std_uri != null && !std_uri.equals("")) {

	    Study std = Study.find(std_uri);
	    if (std == null) {
		message = "ERROR - Could not retrieve study from its URI.";
		return refine(file_name, da_uri, message);
	    }

	    DataAcquisition da = DataAcquisition.findByUri(da_uri);
	    if (da == null) {
		message = "ERROR - Could not retrieve Data Acquisition from its URI.";
		return refine(file_name, da_uri, message);
	    }
	    
	    da.setStudyUri(std_uri);
	    
	    try {
		da.save();
		da.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
	    } catch (CommandException e) {
	    }
	    return ok(prepareIngestion.render(file_name, da, "Updated Data Acquisition with deployment information"));
	}

	message = "DA is now associated with study " + std_uri;
 	return refine(file_name, da_uri, message);
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result processSelectDeployment(String file_name, String da_uri) {
	Form<AssignOptionForm> form = Form.form(AssignOptionForm.class).bindFromRequest();
	String message = "";
        AssignOptionForm data = form.get();
	String dep_uri = data.getOption();
	//System.out.println("Showing returned option: " + dep_uri);

	if (dep_uri != null && !dep_uri.equals("")) {

	    Deployment dep = Deployment.find(dep_uri);
	    if (dep == null) {
		message = "ERROR - Could not retrieve Deployment from its URI.";
		return refine(file_name, da_uri, message);
	    }

	    DataAcquisition da = DataAcquisition.findByUri(da_uri);
	    if (da == null) {
		message = "ERROR - Could not retrieve Data Acquisition from its URI.";
		return refine(file_name, da_uri, message);
	    }
	    
	    da.setDeploymentUri(dep_uri);
	    
	    try {
		da.save();
		da.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
	    } catch (CommandException e) {
	    }
	    return ok(prepareIngestion.render(file_name, da, "Updated Data Acquisition with deployment information"));
	}
	
	message = "DA is now associated with deployment " + dep_uri;
 	return refine(file_name, da_uri, message);
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result processSelectSchema(String file_name, String da_uri) {
	Form<AssignOptionForm> form = Form.form(AssignOptionForm.class).bindFromRequest();
	String message = "";
        AssignOptionForm data = form.get();
	String das_uri = data.getOption();
	//System.out.println("Showing returned option: " + das_uri);

	if (das_uri != null && !das_uri.equals("")) {

	    DataAcquisitionSchema das = DataAcquisitionSchema.find(das_uri);
	    if (das == null) {
		message = "ERROR - Could not retrieve Data Acquisition Schema from its URI.";
		return refine(file_name, da_uri, message);
	    }

	    DataAcquisition da = DataAcquisition.findByUri(da_uri);
	    if (da == null) {
		message = "ERROR - Could not retrieve Data Acquisition from its URI.";
		return refine(file_name, da_uri, message);
	    }
	    
	    da.setSchemaUri(das_uri);
	    
	    try {
		da.save();
		da.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
	    } catch (CommandException e) {
	    }
	    return ok(prepareIngestion.render(file_name, da, "Updated Data Acquisition with data acquisition schema information"));
	}

	message = "DA is now associated with data acquisition schema " + das_uri;
 	return refine(file_name, da_uri, message);
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
	public static Result removeAssociation(String file_name, String da_uri, String daComponent) {
	
	String message = "";
	DataAcquisition da = DataAcquisition.findByUri(da_uri);
	if (da == null) {
	    message = "ERROR - Could not retrieve Data Acquisition from its URI.";
	    return refine(file_name, da_uri, message);
	}
	
	switch (daComponent) {
	case "Study":  
	    da.setStudyUri("");
	    break;
	case "Deployment":  
	    da.setDeploymentUri("");
	    break;
	case "Schema":  
	    da.setSchemaUri("");
	}
	
	try {
	    da.save();
	    da.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
	} catch (CommandException e) {
	}
	message = "Association with " + daComponent + " removed from the Data Acquisition.";
	return ok(prepareIngestion.render(file_name, da, message));
    }
    
}


