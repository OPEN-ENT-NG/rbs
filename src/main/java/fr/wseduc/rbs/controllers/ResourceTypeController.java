package fr.wseduc.rbs.controllers;

import org.entcore.common.controller.ControllerHelper;
import org.vertx.java.core.http.HttpServerRequest;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Post;

public class ResourceTypeController extends ControllerHelper {

	@Post("/type")
	@ApiDoc("Create resource type")
	// @SecuredAction("rbs.type.create")
	public void add(HttpServerRequest request) {
		create(request);
	}
	
}
