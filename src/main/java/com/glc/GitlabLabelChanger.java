package com.glc;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitlabLabelChanger {
    private static final Logger logger = LoggerFactory.getLogger(GitlabLabelChanger.class);
    private static final String GITLAB_API_URL = "https://gitlab.com/api/v4";
    private static final String GITLAB_PRIVATE_TOKEN = "[GITLAB_PRIVATE_TOKEN]";

    public static void main(String[] args) {
        try {
            // 모든 최하위 그룹의 프로젝트 가져오기
            JSONArray leafGroups = getLeafGroups();
            JSONArray allProjects = new JSONArray();

            for (int i = 0; i < leafGroups.length(); i++) {
                JSONObject group = leafGroups.getJSONObject(i);
                int groupId = group.getInt("id");

                JSONArray groupProjects = getGroupProjects(groupId);
                if (groupProjects != null) {
                    for (int j = 0; j < groupProjects.length(); j++) {
                        allProjects.put(groupProjects.getJSONObject(j));
                    }
                } else {
                    logger.info("Failed to fetch projects for group: {}",groupId);
                }
            }

            // 각 프로젝트에 대해 라벨 변경 작업 수행
            for (int i = 0; i < allProjects.length(); i++) {
                JSONObject project = allProjects.getJSONObject(i);
                int projectId = project.getInt("id");

                // Opened Merge Requests 가져오기
                JSONArray mergeRequests = getOpenedMergeRequests(projectId);
                if (mergeRequests != null) {
                    for (int j = 0; j < mergeRequests.length(); j++) {
                        JSONObject mr = mergeRequests.getJSONObject(j);
                        int mrIid = mr.getInt("iid");
                        JSONArray labels = mr.getJSONArray("labels");

                        // 라벨 업데이트
                        JSONArray newLabels = getUpdatedLabels(labels);
                        updateMergeRequestLabels(projectId, mrIid, newLabels);
                    }
                } else {
                    logger.info("Failed to fetch merge requests for project: {}" , projectId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 모든 그룹을 가져오고, 최하위 그룹만 반환하는 메소드
     */
    private static JSONArray getLeafGroups() {
        JSONArray leafGroups = new JSONArray();
        JSONArray allGroups = getAllGroups();

        if (allGroups != null) {
            for (int i = 0; i < allGroups.length(); i++) {
                JSONObject group = allGroups.getJSONObject(i);
                int groupId = group.getInt("id");

                // 하위 그룹이 없는 그룹만 추가
                if (!hasSubgroups(groupId)) {
                    leafGroups.put(group);
                }
            }
        }

        return leafGroups;
    }

    /**
     * 모든 그룹을 가져오는 메소드
     */
    private static JSONArray getAllGroups() {
        try {
            HttpResponse<JsonNode> response = Unirest.get(GITLAB_API_URL + "/groups")
                    .header("Private-Token", GITLAB_PRIVATE_TOKEN)
                    .asJson();

            if (response.getStatus() == 200) {
                return response.getBody().getArray();
            } else {
                logger.info("Failed to fetch groups: {}" , response.getStatusText());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 특정 그룹에 하위 그룹이 있는지 확인하는 메소드
     */
    private static boolean hasSubgroups(int groupId) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(GITLAB_API_URL + "/groups/" + groupId + "/subgroups")
                    .header("Private-Token", GITLAB_PRIVATE_TOKEN)
                    .asJson();

            if (response.getStatus() == 200) {
                JSONArray subgroups = response.getBody().getArray();
                return subgroups.length() > 0;
            } else {
                logger.info("Failed to fetch subgroups for group groupID: {} , statusText: {}" , groupId , response.getStatusText());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 특정 그룹에 속한 모든 프로젝트를 가져오는 메소드
     */
    private static JSONArray getGroupProjects(int groupId) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(GITLAB_API_URL + "/groups/" + groupId + "/projects")
                    .header("Private-Token", GITLAB_PRIVATE_TOKEN)
                    .asJson();

            if (response.getStatus() == 200) {
                return response.getBody().getArray();
            } else {
                logger.info("Failed to fetch projects: {}", response.getStatusText());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Opened Merge Requests를 가져오는 메소드
     */
    private static JSONArray getOpenedMergeRequests(int projectId) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(GITLAB_API_URL + "/projects/" + projectId + "/merge_requests?state=opened")
                    .header("Private-Token", GITLAB_PRIVATE_TOKEN)
                    .asJson();

            if (response.getStatus() == 200) {
                return response.getBody().getArray();
            } else {
                logger.info("Failed to fetch merge requests: {}", response.getStatusText());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 라벨을 업데이트하는 메소드
     */
    private static JSONArray getUpdatedLabels(JSONArray labels) {
        JSONArray newLabels = new JSONArray();
        String smallestLabel = null;
        int smallestValue = Integer.MAX_VALUE;

        // 기존 라벨 중 가장 작은 라벨 찾기
        for (int j = 0; j < labels.length(); j++) {
            String label = labels.getString(j);
            if (label.equals("now")) {
                smallestLabel = "now";
                smallestValue = -1; // 'now'가 가장 작은 값
            } else if (label.startsWith("D-")) {
                int number = Integer.parseInt(label.substring(2));
                if (number < smallestValue) {
                    smallestLabel = label;
                    smallestValue = number;
                }
            } else {
                newLabels.put(label); // 다른 라벨들은 그대로 유지
            }
        }

        // 가장 작은 D-n 또는 'now' 라벨을 한 단계 아래로 변경하여 추가
        if (smallestLabel != null) {
            if (smallestLabel.equals("now")) {
                newLabels.put("now"); // 'now'는 그대로 유지
            } else if (smallestLabel.startsWith("D-")) {
                int number = Integer.parseInt(smallestLabel.substring(2));
                if (number > 1) {
                    newLabels.put("D-" + (number - 1)); // D-n -> D-(n-1)로 변경
                } else {
                    newLabels.put("now"); // D-1 -> now로 변경
                }
            }
        }

        return newLabels;
    }

    /**
     * Merge Request 라벨을 업데이트하는 메소드
     */
    private static void updateMergeRequestLabels(int projectId, int mrId, JSONArray newLabels) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("labels", newLabels);

            HttpResponse<JsonNode> response = Unirest.put(GITLAB_API_URL + "/projects/" + projectId + "/merge_requests/" + mrId)
                    .header("Private-Token", GITLAB_PRIVATE_TOKEN)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .asJson();

            if (response.getStatus() == 200) {
                logger.info("Successfully updated labels for MR #{} , in project: {}", mrId,projectId);
            } else {
                logger.info("Failed to update  labels for MR #{} , in project: {}", mrId,projectId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}