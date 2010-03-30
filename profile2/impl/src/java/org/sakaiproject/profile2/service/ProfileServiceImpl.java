/**
 * Copyright (c) 2008-2010 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakaiproject.profile2.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.profile2.exception.ProfileNotDefinedException;
import org.sakaiproject.profile2.logic.ProfileLogic;
import org.sakaiproject.profile2.logic.SakaiProxy;
import org.sakaiproject.profile2.model.Person;
import org.sakaiproject.profile2.model.ProfilePreferences;
import org.sakaiproject.profile2.model.ProfilePrivacy;
import org.sakaiproject.profile2.model.ProfileStatus;
import org.sakaiproject.profile2.model.SocialNetworkingInfo;
import org.sakaiproject.profile2.model.UserProfile;
import org.sakaiproject.profile2.util.Messages;
import org.sakaiproject.profile2.util.ProfileConstants;
import org.sakaiproject.profile2.util.ProfileUtils;

/**
 * <p>This is the implementation of {@link ProfileService}; see that interface for usage details.
 * 
 * @author Steve Swinsburg (s.swinsburg@lancaster.ac.uk)
 *
 */
public class ProfileServiceImpl implements ProfileService {

	private static final Logger log = Logger.getLogger(ProfileServiceImpl.class);
	
	
	/**
	 * {@inheritDoc}
	 */
	public UserProfile getPrototype() {
		return new UserProfile();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public UserProfile getFullUserProfile(String userId) {
		
		//check auth and get currentUserUuid
		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("Must be logged in.");
		}
		
		//convert userId into uuid
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return null;
		}
		
		//setup obj
		UserProfile userProfile = null;
		
		//get SakaiPerson
		SakaiPerson sakaiPerson = sakaiProxy.getSakaiPerson(userUuid);
		if(sakaiPerson == null) {
			userProfile = getPrototype(userUuid);
			//even though we don't have a real profile, they need an image url, it will just be the default one
			addImageUrlToProfile(userProfile);
			addThumbnailImageUrlToProfile(userProfile);
			
			return userProfile;
		}
		
		//transform
		userProfile = transformSakaiPersonToUserProfile(sakaiPerson);
				
		//if person requested own profile, no need for privacy checks
		//add the additional information and return
		if(userUuid.equals(currentUserUuid)) {
			log.debug("userId is current user");
			addStatusToProfile(userProfile);
			addImageUrlToProfile(userProfile);
			addThumbnailImageUrlToProfile(userProfile);
			
			addBusinessInfoToProfile(userProfile, sakaiPerson);
			addSocialNetworkingInfoToProfile(userProfile);
			
			return userProfile;
			
		}
		
		//get privacy record
		ProfilePrivacy privacy = profileLogic.getPrivacyRecordForUser(userUuid);
		
		//get preferences record
		ProfilePreferences preferences = profileLogic.getPreferencesRecordForUser(userUuid);
		
		//check friend status
		boolean friend = profileLogic.isUserXFriendOfUserY(userUuid, currentUserUuid);
		
		//unset basic info if not allowed
		if(!profileLogic.isUserXBasicInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			log.debug("basic info not allowed");
			userProfile.setNickname(null);
			userProfile.setDateOfBirth(null);
		}
		
		//unset contact info if not allowed
		if(!profileLogic.isUserXContactInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			log.debug("contact info not allowed");
			userProfile.setEmail(null);
			userProfile.setHomepage(null);
			userProfile.setHomephone(null);
			userProfile.setWorkphone(null);
			userProfile.setMobilephone(null);
			userProfile.setFacsimile(null);
		}
		
		//unset contact info if not allowed
		if(!profileLogic.isUserXStaffInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			log.debug("staff info not allowed");
			userProfile.setDepartment(null);
			userProfile.setPosition(null);
			userProfile.setSchool(null);
			userProfile.setRoom(null);
			userProfile.setStaffProfile(null);
			userProfile.setAcademicProfileUrl(null);
			userProfile.setUniversityProfileUrl(null);
			userProfile.setPublications(null);
		}
		
		//unset contact info if not allowed
		if(!profileLogic.isUserXStudentInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			log.debug("student info not allowed");
			userProfile.setCourse(null);
			userProfile.setSubjects(null);
		}
		
		//unset personal info if not allowed
		if(!profileLogic.isUserXPersonalInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			log.debug("personal info not allowed");
			userProfile.setFavouriteBooks(null);
			userProfile.setFavouriteTvShows(null);
			userProfile.setFavouriteMovies(null);
			userProfile.setFavouriteQuotes(null);
			userProfile.setOtherInformation(null);
			
			// social networking fields
			userProfile.setFacebookUsername(null);
			userProfile.setLinkedinUsername(null);
			userProfile.setMyspaceUsername(null);
			userProfile.setSkypeUsername(null);
			userProfile.setTwitterUsername(null);
		} else {
			addSocialNetworkingInfoToProfile(userProfile);
		}
		
		if (profileLogic.isUserXBusinessInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			addBusinessInfoToProfile(userProfile, sakaiPerson);
		}
		
		//profile status
		if(profileLogic.isUserXStatusVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			addStatusToProfile(userProfile);
		}
		
		//add image urls
		addImageUrlToProfile(userProfile);
		addThumbnailImageUrlToProfile(userProfile);
		
		//properties
		addPropertiesToProfile(userProfile, privacy, preferences);
		
		return userProfile;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public UserProfile getMinimalUserProfile(String userId) {
		
		//check auth and get currentUserUuid
		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("You must be logged in to make a request for a user's profile.");
		}
		
		//convert userId into uuid
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return null;
		}
				
		//create base profile
		UserProfile userProfile = getPrototype(userUuid);
		
		//get privacy record for the user - will be done in the method so not necessary here unless we add more fields that may use it
		//ProfilePrivacy profilePrivacy = profile.getPrivacyRecordForUser(userUuid);
		
		//check friend status
		boolean friend = profileLogic.isUserXFriendOfUserY(userUuid, currentUserUuid);
		
		//add status if allowed
		if(profileLogic.isUserXStatusVisibleByUserY(userUuid, currentUserUuid, friend)) {
			addStatusToProfile(userProfile);
		}
		
		//add thumbnail image url
		addThumbnailImageUrlToProfile(userProfile);
		
		return userProfile;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public UserProfile getAcademicUserProfile(String userId) {
		
		//check auth and get currentUserUuid
		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("You must be logged in to make a request for a user's profile.");
		}
		
		//convert userId into uuid
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return null;
		}
		
		//get SakaiPerson
		SakaiPerson sakaiPerson = sakaiProxy.getSakaiPerson(userUuid);
		if(sakaiPerson == null) {
			return getPrototype(userUuid);
		}
		
		//create base profile
		UserProfile userProfile = getPrototype(userUuid);
		
		//get privacy record for the user
		ProfilePrivacy privacy = profileLogic.getPrivacyRecordForUser(userUuid);
		
		//check friend status
		boolean friend = profileLogic.isUserXFriendOfUserY(userUuid, currentUserUuid);
		
		//check if the staff fields are allowed to be viewed by this user.
		if(profileLogic.isUserXStaffInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			addStaffInfoToProfile(userProfile, sakaiPerson);
		}
		
		//check if the student fields are allowed to be viewed by this user.
		if(profileLogic.isUserXStudentInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			addStudentInfoToProfile(userProfile, sakaiPerson);
		}
		
		//add full image url
		addImageUrlToProfile(userProfile);
		
		return userProfile;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public UserProfile getLegacyUserProfile(String userId) {

		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("Must be logged in.");
		}
		
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return null;
		}
		
		UserProfile userProfile = null;
		
		SakaiPerson sakaiPerson = sakaiProxy.getSakaiPerson(userUuid);
		if(sakaiPerson == null) {
			userProfile = getPrototype(userUuid);
			return userProfile;
		}
		
		userProfile = transformSakaiPersonToUserProfile(sakaiPerson);
		
		if(userUuid.equals(currentUserUuid)) {
			log.debug("userId is current user");
			return userProfile;
		}
		
		ProfilePrivacy privacy = profileLogic.getPrivacyRecordForUser(userUuid);
		ProfilePreferences preferences = profileLogic.getPreferencesRecordForUser(userUuid);
		boolean friend = profileLogic.isUserXFriendOfUserY(userUuid, currentUserUuid);
		
		if(!profileLogic.isUserXBasicInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			userProfile.setNickname(null);
			userProfile.setDateOfBirth(null);
		}
		
		if(!profileLogic.isUserXContactInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			userProfile.setEmail(null);
			userProfile.setHomepage(null);
			userProfile.setHomephone(null);
			userProfile.setWorkphone(null);
			userProfile.setMobilephone(null);
			userProfile.setFacsimile(null);
		}
		
		if(!profileLogic.isUserXStaffInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			userProfile.setDepartment(null);
			userProfile.setPosition(null);
			userProfile.setSchool(null);
			userProfile.setRoom(null);
			userProfile.setStaffProfile(null);
			userProfile.setAcademicProfileUrl(null);
			userProfile.setUniversityProfileUrl(null);
			userProfile.setPublications(null);
		}
		
		if(!profileLogic.isUserXStudentInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			userProfile.setPosition(null);
			userProfile.setDepartment(null);
			userProfile.setSchool(null);
			userProfile.setRoom(null);
			userProfile.setCourse(null);
			userProfile.setSubjects(null);
		}
		
		if(!profileLogic.isUserXPersonalInfoVisibleByUserY(userUuid, privacy, currentUserUuid, friend)) {
			userProfile.setFavouriteBooks(null);
			userProfile.setFavouriteTvShows(null);
			userProfile.setFavouriteMovies(null);
			userProfile.setFavouriteQuotes(null);
			userProfile.setOtherInformation(null);
		}

		//unset info that isn't used by the legacy Profile
		userProfile.setDateOfBirth(null);
		userProfile.setCourse(null);
		userProfile.setSubjects(null);
		userProfile.setFavouriteBooks(null);
		userProfile.setFavouriteTvShows(null);
		userProfile.setFavouriteMovies(null);
		userProfile.setFavouriteQuotes(null);
		
		
		return userProfile;
	}

	/**
	 * {@inheritDoc}
	 */
	public UserProfile getCustomUserProfile(String userId, int profileType) {
		return null;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean checkUserExists(String userId) {
		return sakaiProxy.checkForUser(sakaiProxy.getUuidForUserId(userId));
	}
	
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public List<Person> getConnectionsForUser(String userId) {
		
		//check auth and get currentUserUuid
		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("You must be logged in to make a request for a user's connections.");
		}
		
		//convert userId into uuid
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return null;
		}
				
		//check friend status
		boolean friend = profileLogic.isUserXFriendOfUserY(userUuid, currentUserUuid);
		
		List<Person> connections = new ArrayList<Person>();
		
		if(profileLogic.isUserXFriendsListVisibleByUserY(userUuid, currentUserUuid, friend)) {
			connections = profileLogic.getConnectionsForUser(userUuid);
		}
		return connections;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getUserProfileAsHTML(UserProfile userProfile) {
		
		//note there is no birthday in this field. we need a good way to get the birthday without the year. 
		//maybe it needs to be stored in a separate field and treated differently. Or returned as a localised string.
		
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"profile2-profile\">");
		
		boolean useThumbnail = true;
		
		if(StringUtils.isNotBlank(userProfile.getImageUrl())) {
			sb.append("<div class=\"profile2-profile-image\">");
			sb.append("<img src=\"");
			sb.append(userProfile.getImageUrl());
			sb.append("\" />");
			sb.append("</div>");
			useThumbnail = false;
		}
		
		//only add thumbnail if no main image has been used
		if(useThumbnail && StringUtils.isNotBlank(userProfile.getImageThumbUrl())) {
			sb.append("<div class=\"profile2-profile-image-thumb\">");
			sb.append("<img src=\"");
			sb.append(userProfile.getImageThumbUrl());
			sb.append("\" />");
			sb.append("</div>");
		}
		
		//diff styles depending on if thumb was used or not, for diff widths in formatted profile view.
		if(useThumbnail) {
			sb.append("<div class=\"profile2-profile-content-thumb\">");
		} else {
			sb.append("<div class=\"profile2-profile-content\">");
		}
		
		if(StringUtils.isNotBlank(userProfile.getUserUuid())) {
			sb.append("<div class=\"profile2-profile-userUuid\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.userUuid"));
			sb.append("</span>");
			sb.append(userProfile.getUserUuid());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getDisplayName())) {
			sb.append("<div class=\"profile2-profile-displayName\">");
			sb.append(userProfile.getDisplayName());
			sb.append("</div>");
		}
		
		//status
		if(StringUtils.isNotBlank(userProfile.getStatusMessage())) {
			sb.append("<div class=\"profile2-profile-statusMessage\">");
			sb.append(userProfile.getStatusMessage());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getStatusDateFormatted())) {
			sb.append("<div class=\"profile2-profile-statusDate\">");
			sb.append(userProfile.getStatusDateFormatted());
			sb.append("</div>");
		}
		
		//basic info
		if(StringUtils.isNotBlank(userProfile.getNickname())) {
			sb.append("<div class=\"profile2-profile-nickname\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.nickname"));
			sb.append("</span>");
			sb.append(userProfile.getNickname());
			sb.append("</div>");
		}
		
		
		
		//contact info
		if(StringUtils.isNotBlank(userProfile.getEmail())) {
			sb.append("<div class=\"profile2-profile-email\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.email"));
			sb.append("</span>");
			sb.append(userProfile.getEmail());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getHomepage())) {
			sb.append("<div class=\"profile2-profile-homepage\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.homepage"));
			sb.append("</span>");
			sb.append(userProfile.getHomepage());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getHomephone())) {
			sb.append("<div class=\"profile2-profile-homephone\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.homephone"));
			sb.append("</span>");
			sb.append(userProfile.getHomephone());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getWorkphone())) {
			sb.append("<div class=\"profile2-profile-workphone\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.workphone"));
			sb.append("</span>");
			sb.append(userProfile.getWorkphone());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getMobilephone())) {
			sb.append("<div class=\"profile2-profile-mobilephone\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.mobilephone"));
			sb.append("</span>");
			sb.append(userProfile.getMobilephone());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getFacsimile())) {
			sb.append("<div class=\"profile2-profile-facsimile\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.facsimile"));
			sb.append("</span>");
			sb.append(userProfile.getFacsimile());
			sb.append("</div>");
		}
		
		
		
		//academic info
		if(StringUtils.isNotBlank(userProfile.getPosition())) {
			sb.append("<div class=\"profile2-profile-position\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.position"));
			sb.append("</span>");
			sb.append(userProfile.getPosition());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getDepartment())) {
			sb.append("<div class=\"profile2-profile-department\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.department"));
			sb.append("</span>");
			sb.append(userProfile.getDepartment());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getSchool())) {
			sb.append("<div class=\"profile2-profile-school\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.school"));
			sb.append("</span>");
			sb.append(userProfile.getSchool());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getRoom())) {
			sb.append("<div class=\"profile2-profile-room\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.room"));
			sb.append("</span>");
			sb.append(userProfile.getRoom());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getCourse())) {
			sb.append("<div class=\"profile2-profile-course\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.course"));
			sb.append("</span>");
			sb.append(userProfile.getCourse());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getSubjects())) {
			sb.append("<div class=\"profile2-profile-subjects\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.subjects"));
			sb.append("</span>");
			sb.append(userProfile.getSubjects());
			sb.append("</div>");
		}
		
		
		//personal info
		if(StringUtils.isNotBlank(userProfile.getFavouriteBooks())) {
			sb.append("<div class=\"profile2-profile-favouriteBooks\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.favouriteBooks"));
			sb.append("</span>");
			sb.append(userProfile.getFavouriteBooks());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getFavouriteTvShows())) {
			sb.append("<div class=\"profile2-profile-favouriteTvShows\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.favouriteTvShows"));
			sb.append("</span>");
			sb.append(userProfile.getFavouriteTvShows());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getFavouriteMovies())) {
			sb.append("<div class=\"profile2-profile-favouriteMovies\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.favouriteMovies"));
			sb.append("</span>");
			sb.append(userProfile.getFavouriteMovies());
			sb.append("</div>");
		}
		
		if(StringUtils.isNotBlank(userProfile.getFavouriteQuotes())) {
			sb.append("<div class=\"profile2-profile-favouriteQuotes\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.favouriteQuotes"));
			sb.append("</span>");

			sb.append(userProfile.getFavouriteQuotes());
			sb.append("</div>");
		}
		if(StringUtils.isNotBlank(userProfile.getOtherInformation())) {
			sb.append("<div class=\"profile2-profile-otherInformation\">");
			sb.append("<span class=\"profile2-profile-label\">");
			sb.append(Messages.getString("Label.otherInformation"));
			sb.append("</span>");
			sb.append(userProfile.getOtherInformation());
			sb.append("</div>");
		}
		
		sb.append("</div>");
		sb.append("</div>");
		
		//add the stylesheet
		sb.append("<link href=\"");
		sb.append(ProfileConstants.ENTITY_CSS_PROFILE);
		sb.append("\" type=\"text/css\" rel=\"stylesheet\" media=\"all\" />");
		
		return sb.toString();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean save(UserProfile userProfile) {
		
		//check auth and get currentUserUuid
		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("Must be logged in.");
		}
		
		//check currentUser and profile uuid match
		if(!currentUserUuid.equals(userProfile.getUserUuid())) {
			throw new SecurityException("Not allowed to save.");
		}
		
		//translate, save and return the response.
		return persistUserProfile(userProfile);
		
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean create(String userId) {
		
		//check auth and get currentUserUuid
		String currentUserUuid = sakaiProxy.getCurrentUserId();
		if(currentUserUuid == null) {
			throw new SecurityException("Must be logged in.");
		}
		
		//convert userId into uuid
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return false;
		}
		
		//check currentUser and profile uuid match
		if(!currentUserUuid.equals(userUuid)) {
			throw new SecurityException("Not allowed to save.");
		}
		
		//does this user already have a persisted profile?
		if(checkUserProfileExists(userUuid)) {
			log.error("userUuid: " + userUuid + " already has a profile. Cannot create another.");
			return false;
		}
			
		//no existing profile, setup a prototype
		UserProfile userProfile = getPrototype(userUuid);
		
		//translate, save and return the response.
		return persistUserProfile(userProfile);
				
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean create(UserProfile userProfile) {
		
		String userUuid = userProfile.getUserUuid();
		if(StringUtils.isBlank(userUuid)) {
			return false;
		}
		
		//does this user already have a persisted profile?
		if(checkUserProfileExists(userUuid)) {
			log.error("userUuid: " + userUuid + " already has a profile. Cannot create another.");
			return false;
		}
		
		return save(userProfile);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean checkUserProfileExists(String userId) {
		
		//convert userId into uuid
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		if(userUuid == null) {
			log.error("Invalid userId: " + userId);
			return false;
		}
		
		//check if we have a persisted object already
		if(sakaiProxy.getSakaiPerson(userUuid) == null) {
			return false;
		}
		return true;
	}
	
	
	
	
	/**
	 * This is a helper method to take care of translating a UserProfile to a SakaiPerson, doing anything else
	 * then persisting it to the database.
	 * 
	 * @param userProfile
	 * @return true/false for success
	 */
	private boolean persistUserProfile(UserProfile userProfile) {
		
		//translate main fields
		SakaiPerson sakaiPerson = transformUserProfileToSakaiPerson(userProfile);

		//update SakaiPerson obj
		if(sakaiProxy.updateSakaiPerson(sakaiPerson)) {
			
			//TODO the fields that can update the Account need to be done as well, if allowed.
			
			//TODO if profile is locked,should not update, but will need to get the existing record if exists, then check that.
			
			return true;
		} 
		return false;
		
	}
	
	
	
	// TODO
	private void applyPrivacyChecksToUserProfile(UserProfile userProfile, ProfilePrivacy privacy, boolean friend) {
		
		//go over the various sections of the profile, see if a user is allowed to see them or not, and null out if not.
		//this should replace the checks above
		
	}
	

	/**
	 * These are two helper methods to simply add the URL to a user's profile image or thumbnail to the UserProfile. 
	 * It can be added to any profile without checks as the retrieval of the image does the checks, and a default image
	 * is used if not allowed or none available. The UserProfile must have a userUuid in it first though.
	 * 
	 * @param userProfile
	 */
	private void addImageUrlToProfile(UserProfile userProfile) {
		userProfile.setImageUrl(sakaiProxy.getServerUrl() + "/direct/profile/" + userProfile.getUserUuid() + "/image/");
	}
	
	private void addThumbnailImageUrlToProfile(UserProfile userProfile) {
		userProfile.setImageThumbUrl(sakaiProxy.getServerUrl() + "/direct/profile/" + userProfile.getUserUuid() + "/image/thumb/");
	}
	
	
	
	
	
	
	
	/**
	 * This is a helper method to take care of getting the status and adding it to the profile.
	 * It is only called after any necessary privacy checks have been made
	 * @param userProfile	- UserProfile object for the person
	 */
	private void addStatusToProfile(UserProfile userProfile) {
		ProfileStatus profileStatus = profileLogic.getUserStatus(userProfile.getUserUuid());
		if(profileStatus != null) {
			userProfile.setStatusMessage(profileStatus.getMessage());
			userProfile.setStatusDate(profileStatus.getDateAdded());
			
			userProfile.setStatusDateFormatted(ProfileUtils.convertDateForStatus(userProfile.getStatusDate()));
		}
	}
	
	/**
	 * This is a helper method to take the values from SakaiPerson and add to UserProfile
	 * 
	 * TODO have one of these helpers for each block of info we get from SakaiPerson and abstract methods to use these.
	 * The blocks have been added below they just need to be used
	 * 
	 * @param userProfile
	 * @param sp
	 */
	private void addStaffInfoToProfile(UserProfile userProfile, SakaiPerson sp) {
		userProfile.setDepartment(sp.getOrganizationalUnit());
		userProfile.setPosition(sp.getTitle());
		userProfile.setSchool(sp.getCampus());
		userProfile.setRoom(sp.getRoomNumber());
		userProfile.setStaffProfile(sp.getStaffProfile());
		userProfile.setAcademicProfileUrl(sp.getAcademicProfileUrl());
		userProfile.setUniversityProfileUrl(sp.getUniversityProfileUrl());
		userProfile.setPublications(sp.getPublications());
	}
	
	/**
	 * This is a helper method to take the values from SakaiPerson and add to UserProfile
	 * 
	 * TODO have one of these helpers for each block of info we get from SakaiPerson and abstract methods to use these.
	 * The blocks have been added below they just need to be used
	 * 
	 * @param userProfile
	 * @param sp
	 */
	private void addStudentInfoToProfile(UserProfile userProfile, SakaiPerson sp) {
		userProfile.setCourse(sp.getEducationCourse());
		userProfile.setSubjects(sp.getEducationSubjects());
	}
	
	/**
	 * Helper method to set contact info into profile
	 * @param userProfile
	 * @param sp
	 */
	private void addContactInfoToProfile(UserProfile userProfile, SakaiPerson sp) {
		String userUuid = userProfile.getUserUuid();
		userProfile.setEmail(sakaiProxy.getUserEmail(userUuid));
		userProfile.setHomepage(sp.getLabeledURI());
		userProfile.setWorkphone(sp.getTelephoneNumber());
		userProfile.setHomephone(sp.getHomePhone());
		userProfile.setMobilephone(sp.getMobile());
		userProfile.setFacsimile(sp.getFacsimileTelephoneNumber());
	}
	
	/**
	 * Helper method to set basic info into profile
	 * @param userProfile
	 * @param sp
	 */
	private void addBasicInfoToProfile(UserProfile userProfile, SakaiPerson sp) {
		userProfile.setNickname(sp.getNickname());
		userProfile.setDateOfBirth(sp.getDateOfBirth());
	}
	
	/**
	 * Helper method to set company profiles into profile.
	 * 
	 * @param userProfile
	 */
	private void addBusinessInfoToProfile(UserProfile userProfile,
			SakaiPerson sp) {
		userProfile.setBusinessBiography(sp.getBusinessBiography());
		userProfile.setCompanyProfiles(profileLogic
				.getCompanyProfiles(userProfile.getUserUuid()));
	}
	
	/**
	 * Helper method to set social networking info into profile.
	 * 
	 * @param userProfile
	 */
	private void addSocialNetworkingInfoToProfile(UserProfile userProfile) {
		SocialNetworkingInfo socialNetworkingInfo = profileLogic
				.getSocialNetworkingInfo(userProfile.getUserUuid());
		
		if (null == socialNetworkingInfo) {
			return;
		}
		
		userProfile.setFacebookUsername(socialNetworkingInfo.getFacebookUsername());
		userProfile.setLinkedinUsername(socialNetworkingInfo.getLinkedinUsername());
		userProfile.setMyspaceUsername(socialNetworkingInfo.getMyspaceUsername());
		userProfile.setSkypeUsername(socialNetworkingInfo.getSkypeUsername());
		userProfile.setTwitterUsername(socialNetworkingInfo.getTwitterUsername());
	}

	/**
	 * Helper method to set personal info into profile
	 * @param userProfile
	 * @param sp
	 */
	private void addPersonalInfoToProfile(UserProfile userProfile, SakaiPerson sp) {
		userProfile.setFavouriteBooks(sp.getFavouriteBooks());
		userProfile.setFavouriteTvShows(sp.getFavouriteTvShows());
		userProfile.setFavouriteMovies(sp.getFavouriteMovies());
		userProfile.setFavouriteQuotes(sp.getFavouriteQuotes());
		userProfile.setOtherInformation(sp.getNotes());
	}
	
	
	
	/**
	 * This is a helper method to take care of setting the various relevant properties into a user's profile.
	 * If a user has requested their own profile, they dont need these properties (?)
	 * 
	 * @param userProfile		- UserProfile object for the person
	 * @param privacy			- Privacy object for the person
	 * @param preferences	- Preferences object for the person
	 */
	private void addPropertiesToProfile(UserProfile userProfile, ProfilePrivacy privacy, ProfilePreferences preferences) {
		
		if(privacy == null) {
			userProfile.setProperty(ProfileConstants.PROP_BIRTH_YEAR_VISIBLE, String.valueOf(ProfileConstants.DEFAULT_BIRTHYEAR_VISIBILITY));
		} else {
			userProfile.setProperty(ProfileConstants.PROP_BIRTH_YEAR_VISIBLE, String.valueOf(privacy.isShowBirthYear()));
		}
		
		if(preferences == null) {
			userProfile.setProperty(ProfileConstants.PROP_EMAIL_CONFIRM_ENABLED, String.valueOf(ProfileConstants.DEFAULT_EMAIL_NOTIFICATION_SETTING));
			userProfile.setProperty(ProfileConstants.PROP_EMAIL_REQUEST_ENABLED, String.valueOf(ProfileConstants.DEFAULT_EMAIL_NOTIFICATION_SETTING));
		} else {
			userProfile.setProperty(ProfileConstants.PROP_EMAIL_CONFIRM_ENABLED, String.valueOf(preferences.isConfirmEmailEnabled()));
			userProfile.setProperty(ProfileConstants.PROP_EMAIL_REQUEST_ENABLED, String.valueOf(preferences.isRequestEmailEnabled()));
		}

	}
	
		
	
	/**
	 * Convenience method to map a SakaiPerson object onto a UserProfile object
	 * 
	 * @param sp 		input SakaiPerson
	 * @return			returns a UserProfile representation of the SakaiPerson object
	 */
	private UserProfile transformSakaiPersonToUserProfile(SakaiPerson sp) {
		
		String userUuid = sp.getAgentUuid();
		
		UserProfile userProfile = new UserProfile();
		
		//map fields from SakaiPerson to UserProfile
		
		//minimum info
		userProfile.setUserUuid(userUuid);
		userProfile.setDisplayName(sakaiProxy.getUserDisplayName(userUuid));

		//basic info
		userProfile.setNickname(sp.getNickname());
		userProfile.setDateOfBirth(sp.getDateOfBirth());
		
		//contact info
		userProfile.setEmail(sakaiProxy.getUserEmail(userUuid));
		userProfile.setHomepage(sp.getLabeledURI());
		userProfile.setWorkphone(sp.getTelephoneNumber());
		userProfile.setHomephone(sp.getHomePhone());
		userProfile.setMobilephone(sp.getMobile());
		userProfile.setFacsimile(sp.getFacsimileTelephoneNumber());
		
		//staff info
		userProfile.setDepartment(sp.getOrganizationalUnit());
		userProfile.setPosition(sp.getTitle());
		userProfile.setSchool(sp.getCampus());
		userProfile.setRoom(sp.getRoomNumber());
		userProfile.setStaffProfile(sp.getStaffProfile());
		userProfile.setAcademicProfileUrl(sp.getAcademicProfileUrl());
		userProfile.setUniversityProfileUrl(sp.getUniversityProfileUrl());
		userProfile.setPublications(sp.getPublications());
		
		//student info
		userProfile.setCourse(sp.getEducationCourse());
		userProfile.setSubjects(sp.getEducationSubjects());
		
		//personal info
		userProfile.setFavouriteBooks(sp.getFavouriteBooks());
		userProfile.setFavouriteTvShows(sp.getFavouriteTvShows());
		userProfile.setFavouriteMovies(sp.getFavouriteMovies());
		userProfile.setFavouriteQuotes(sp.getFavouriteQuotes());
		userProfile.setOtherInformation(sp.getNotes());
		
		//business info
		userProfile.setBusinessBiography(sp.getBusinessBiography());
		
		return userProfile;
		
	}
	
	/**
	 * Convenience method to map a UserProfile object onto a SakaiPerson object for persisting
	 * 
	 * @param up 		input SakaiPerson
	 * @return			returns a SakaiPerson representation of the UserProfile object
	 */
	private SakaiPerson transformUserProfileToSakaiPerson(UserProfile up) {
	
		String userUuid = up.getUserUuid();
		
		//get SakaiPerson
		SakaiPerson sakaiPerson = sakaiProxy.getSakaiPerson(userUuid);
		
		//if null, create one 
		if(sakaiPerson == null) {
			sakaiPerson = sakaiProxy.createSakaiPerson(userUuid);
			//if its still null, throw exception
			if(sakaiPerson == null) {
				throw new ProfileNotDefinedException("Couldn't create a SakaiPerson for " + userUuid);
			}
		} 
		
		//map fields from UserProfile to SakaiPerson
		
		//basic info
		sakaiPerson.setNickname(up.getNickname());
		sakaiPerson.setDateOfBirth(up.getDateOfBirth());
		
		//contact info
		sakaiPerson.setLabeledURI(up.getHomepage());
		sakaiPerson.setTelephoneNumber(up.getWorkphone());
		sakaiPerson.setHomePhone(up.getHomephone());
		sakaiPerson.setMobile(up.getMobilephone());
		sakaiPerson.setFacsimileTelephoneNumber(up.getFacsimile());
		
		//academic info
		sakaiPerson.setOrganizationalUnit(up.getDepartment());
		sakaiPerson.setTitle(up.getPosition());
		sakaiPerson.setCampus(up.getSchool());
		sakaiPerson.setRoomNumber(up.getRoom());
		sakaiPerson.setEducationCourse(up.getCourse());
		sakaiPerson.setEducationSubjects(up.getSubjects());
		
		//personal info
		sakaiPerson.setFavouriteBooks(up.getFavouriteBooks());
		sakaiPerson.setFavouriteTvShows(up.getFavouriteTvShows());
		sakaiPerson.setFavouriteMovies(up.getFavouriteMovies());
		sakaiPerson.setFavouriteQuotes(up.getFavouriteQuotes());
		sakaiPerson.setNotes(up.getOtherInformation());

		return sakaiPerson;
	}
	
	/**
	 * Create a UserProfile object for the given user. This is the minimum that a UserProfile can be. 
	 * 
	 * @param userId - either internal user id (6ec73d2a-b4d9-41d2-b049-24ea5da03fca) or eid (jsmith26)
	 * @return the minimum UserProfile for the user, ie name only
	 */
	private UserProfile getPrototype(String userId) {
		String userUuid = sakaiProxy.getUuidForUserId(userId);
		
		UserProfile userProfile = getPrototype();
		userProfile.setUserUuid(userUuid);
		userProfile.setDisplayName(sakaiProxy.getUserDisplayName(userUuid));
		
		return userProfile;
	}
	
	private SakaiProxy sakaiProxy;
	public void setSakaiProxy(SakaiProxy sakaiProxy) {
		this.sakaiProxy = sakaiProxy;
	}
	
	private ProfileLogic profileLogic;
	public void setProfileLogic(ProfileLogic profileLogic) {
		this.profileLogic = profileLogic;
	}

	
	

}
