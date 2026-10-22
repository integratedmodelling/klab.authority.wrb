package org.integratedmodelling.klab.authority.wrb;

import java.net.URL;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;

record WrbIdentity(String id, String conceptName, String authorityName, String baseIdentity,
    List<String> parentIds, String label, String description, String locator,
    List<Notification> notifications) implements Authority.Identity {
  WrbIdentity { parentIds = List.copyOf(parentIds); notifications = List.copyOf(notifications); }
  @Override public String getId() { return id; }
  @Override public String getConceptName() { return conceptName; }
  @Override public String getAuthorityName() { return authorityName; }
  @Override public String getBaseIdentity() { return baseIdentity; }
  @Override public List<String> getParentIds() { return parentIds; }
  @Override public List<String> getParentRelationship() { return List.of(); }
  @Override public String getLabel() { return label; }
  @Override public String getDescription() { return description; }
  @Override public String getLocator() { return locator; }
  @Override public List<Notification> getNotifications() { return notifications; }
  @Override public float getScore() { return notifications.isEmpty() ? 1 : 0; }
  @Override public Map<String, URL> getDocumentation() {
    try { return Map.of("application/pdf", new java.net.URI(WRBAuthority.SOURCE).toURL()); }
    catch (java.net.URISyntaxException | java.net.MalformedURLException e) { throw new IllegalStateException(e); }
  }
}
