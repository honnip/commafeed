package com.commafeed.frontend.rest.resources;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.StringUtils;
import org.codehaus.enunciate.doc.DocumentationGroup;

import com.commafeed.backend.StartupBean;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole;
import com.commafeed.backend.model.UserRole.Role;
import com.commafeed.backend.model.UserSettings.ReadingOrder;
import com.commafeed.frontend.SecurityCheck;
import com.commafeed.frontend.model.UserModel;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@SecurityCheck(Role.ADMIN)
@Path("admin/users")
@DocumentationGroup("User Management")
public class AdminUsersREST extends AbstractREST {

	@Path("save")
	@POST
	public Response save(UserModel userModel) {
		Preconditions.checkNotNull(userModel);
		Preconditions.checkNotNull(userModel.getName());

		Long id = userModel.getId();
		if (id == null) {
			Preconditions.checkNotNull(userModel.getPassword());

			Set<Role> roles = Sets.newHashSet(Role.USER);
			if (userModel.isAdmin()) {
				roles.add(Role.ADMIN);
			}

			User user = userService.register(userModel.getName(),
					userModel.getPassword(), roles);
			if (user == null) {
				return Response.status(Status.CONFLICT)
						.entity("User already exists.").build();
			}
		} else {
			User user = userDAO.findById(id);
			if (StartupBean.ADMIN_NAME.equals(user.getName())
					&& !userModel.isEnabled()) {
				return Response.status(Status.FORBIDDEN)
						.entity("You cannot disable the admin user.").build();
			}
			user.setName(userModel.getName());
			if (StringUtils.isNotBlank(userModel.getPassword())) {
				user.setPassword(encryptionService.getEncryptedPassword(
						userModel.getPassword(), user.getSalt()));
			}
			user.setDisabled(!userModel.isEnabled());
			userDAO.update(user);

			Set<Role> roles = userRoleDAO.findRoles(user);
			if (userModel.isAdmin() && !roles.contains(Role.ADMIN)) {
				userRoleDAO.save(new UserRole(user, Role.ADMIN));
			} else if (!userModel.isAdmin() && roles.contains(Role.ADMIN)) {
				if (StartupBean.ADMIN_NAME.equals(user.getName())) {
					return Response
							.status(Status.FORBIDDEN)
							.entity("You cannot remove the admin role from the admin user.")
							.build();
				}
				for (UserRole userRole : userRoleDAO.findAll(user)) {
					if (userRole.getRole() == Role.ADMIN) {
						userRoleDAO.delete(userRole);
					}
				}
			}

		}
		return Response.ok(Status.OK).entity("OK").build();

	}

	@Path("get")
	@GET
	public UserModel getUser(@QueryParam("id") Long id) {
		User user = userDAO.findById(id);
		UserModel userModel = new UserModel();
		userModel.setId(user.getId());
		userModel.setName(user.getName());
		userModel.setEnabled(!user.isDisabled());
		for (UserRole role : userRoleDAO.findAll(user)) {
			if (role.getRole() == Role.ADMIN) {
				userModel.setAdmin(true);
			}
		}
		return userModel;
	}

	@Path("getAll")
	@GET
	public Collection<UserModel> getUsers() {
		Map<Long, UserModel> users = Maps.newHashMap();
		for (UserRole role : userRoleDAO.findAll()) {
			User user = role.getUser();
			Long key = user.getId();
			UserModel userModel = users.get(key);
			if (userModel == null) {
				userModel = new UserModel();
				userModel.setId(user.getId());
				userModel.setName(user.getName());
				userModel.setEnabled(!user.isDisabled());
				users.put(key, userModel);
			}
			if (role.getRole() == Role.ADMIN) {
				userModel.setAdmin(true);
			}
		}
		return users.values();
	}

	@Path("delete")
	@GET
	public Response delete(@QueryParam("id") Long id) {
		User user = userDAO.findById(id);
		if (user == null) {
			return Response.status(Status.NOT_FOUND).build();
		}
		if (StartupBean.ADMIN_NAME.equals(user.getName())) {
			return Response.status(Status.FORBIDDEN)
					.entity("You cannot delete the admin user.").build();
		}
		feedEntryStatusDAO.delete(feedEntryStatusDAO.findAll(user,
				false, ReadingOrder.desc, false));
		feedSubscriptionDAO.delete(feedSubscriptionDAO.findAll(user));
		feedCategoryDAO.delete(feedCategoryDAO.findAll(user));
		userSettingsDAO.delete(userSettingsDAO.findByUser(user));
		userRoleDAO.delete(userRoleDAO.findAll(user));
		userDAO.delete(user);

		return Response.ok().build();
	}
}
