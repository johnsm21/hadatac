package org.hadatac.console.controllers.studies;

import org.hadatac.console.http.GetSparqlQuery;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import play.mvc.Controller;
import play.mvc.Result;
import play.twirl.api.Html;
import play.data.*;

import org.hadatac.console.views.html.*;
import org.hadatac.console.views.html.studies.*;
import org.hadatac.console.views.html.triplestore.syncLabkey;
import org.hadatac.console.controllers.studies.routes;
import org.hadatac.data.api.DataFactory;
import org.hadatac.entity.pojo.Agent;
import org.hadatac.entity.pojo.Study;
import org.hadatac.entity.pojo.StudyType;
import org.hadatac.entity.pojo.TriggeringEvent;
import org.hadatac.metadata.loader.LabkeyDataHandler;
import org.hadatac.metadata.loader.ValueCellProcessing;
import org.hadatac.utils.ConfigProp;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.query.SaveRowsResponse;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;

import org.hadatac.console.models.StudyForm;
import org.hadatac.console.models.SparqlQuery;
import org.hadatac.console.models.SparqlQueryResults;
import org.hadatac.console.models.SysUser;
import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.controllers.dataacquisitionmanagement.DataAcquisitionManagement;
import org.hadatac.console.controllers.triplestore.UserManagement;

public class NewStudy extends Controller {
	
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result index() {
    	if (session().get("LabKeyUserName") == null && session().get("LabKeyPassword") == null) {
    		return redirect(org.hadatac.console.controllers.triplestore.routes.LoadKB.logInLabkey(
    				routes.NewStudy.index().url()));
    	}
    	
	List<Agent> organizations = Agent.findOrganizations();
	List<Agent> persons = Agent.findPersons();
	StudyType studyType = new StudyType();

    	return ok(newStudy.render(studyType, organizations, persons));
    	
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result postIndex() {
    	return index();
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public static Result processForm() {
    	final SysUser sysUser = AuthApplication.getLocalUser(session());
	
        Form<StudyForm> form = Form.form(StudyForm.class).bindFromRequest();
        StudyForm data = form.get();
        
        if (form.hasErrors()) {
            return badRequest("The submitted form has errors!");
        }
        
	// store new values
	String newURI = ValueCellProcessing.replacePrefixEx(data.getNewUri());
	if (newURI == null || newURI.equals("")) {
            return badRequest("[ERROR] New URI cannot be empty.");
	}
	String newStudyType = ValueCellProcessing.replacePrefixEx(data.getNewType());
	String newLabel = data.getNewLabel();
	String newTitle = data.getNewTitle();
	String newProject = data.getNewProject();
	String newComment = data.getNewComment();
	String newInstitution = data.getNewInstitution();
	String newAgent = data.getNewAgent();
	String newStartDateTime = data.getNewStartDateTime();
	String newEndDateTime = data.getNewEndDateTime();

        // insert current state of the STD
	Study std = new Study(newURI,
			      newStudyType,
			      newLabel,
			      newTitle,
			      newProject,
			      newComment,
			      newInstitution,
			      newAgent,
			      newStartDateTime,
			      newStartDateTime);
	
	// insert the new STD content inside of the triplestore regardless of any change -- the previous content has already been deleted
	std.save();
	
	// update/create new STD in LabKey
	int nRowsAffected = std.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
	if (nRowsAffected <= 0) {
	    return badRequest("Failed to insert new STD to LabKey!\n");
	}
	return ok(newStudyConfirm.render(std));
    }
}
