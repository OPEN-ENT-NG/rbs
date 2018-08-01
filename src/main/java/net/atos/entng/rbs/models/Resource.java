package net.atos.entng.rbs.models;

import static net.atos.entng.rbs.BookingUtils.getLocalAdminScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.entcore.common.user.UserInfos;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Resource {
	private final JsonObject json;

	public Resource(JsonObject resource) {
		super();
		this.json = resource;
	}

	public Optional<Long> getMaxDelayAsSeconds() {
		return Optional.ofNullable(json.getLong("max_delay", null));
	}

	public Optional<Long> getMinDelayAsSeconds() {
		return Optional.ofNullable(json.getLong("min_delay", null));
	}

	public Optional<String> getOwner() {
		return Optional.ofNullable(json.getString("owner", null));
	}

	public Optional<String> getSchoolId() {
		return Optional.ofNullable(json.getString("school_id", null));
	}

	public Optional<String> getManagers() {
		return Optional.ofNullable(json.getString("managers", null));
	}

	public boolean hasNotOwnerOrSchoolId() {
		return !getOwner().isPresent() || !getSchoolId().isPresent();
	}

	public Optional<JsonArray> getManagersAsJsonArray() {
		Optional<String> managersString = getManagers();
		JsonArray managers = (managersString.isPresent()) ? new JsonArray(managersString.get()) : null;
		return Optional.ofNullable(managers);
	}

	/*
	 * Owner or managers of a resourceType, as well as local administrators of a
	 * resourceType's schoolId, do no need to respect constraints on resources'
	 * delays
	 */
	public boolean canBypassDelaysConstraints(UserInfos user) {
		String owner = getOwner().orElse(null);
		String schoolId = getSchoolId().orElse(null);
		JsonArray managers = getManagersAsJsonArray().orElse(null);

		if (user.getUserId().equals(owner)) {
			return true;
		}

		List<String> scope = getLocalAdminScope(user);
		if (scope != null && !scope.isEmpty() && scope.contains(schoolId)) {
			return true;
		}

		if (managers != null && managers.size() > 0) {
			// Create a list containing userId and groupIds of current user
			List<String> userAndGroupIds = new ArrayList<>();
			userAndGroupIds.add(user.getUserId());
			userAndGroupIds.addAll(user.getGroupsIds());

			// Return true if managers and userAndGroupIds have at least one common element
			if (!Collections.disjoint(userAndGroupIds, managers.getList())) {
				return true;
			}
		}

		return false;
	}
}
