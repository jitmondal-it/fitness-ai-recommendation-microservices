package com.fitness.aiservice.service;


import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@AllArgsConstructor
public class ActivityAIService {

    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity) {

        log.info("========== AI PROCESSING STARTED ==========");

        String prompt = createPromptForActivity(activity);

        log.info("Prompt created successfully");

        log.info("Calling Gemini API...");

        String aiResponse = geminiService.getRecommendation(prompt);

        log.info("========== AI RECOMMENDATION ==========");
        log.info("{}", aiResponse);
        log.info("========================================");

        return processAIResponse(activity,aiResponse);
    }

    private Recommendation processAIResponse(Activity activity, String aiResponse) {

        try {

            ObjectMapper mapper = new ObjectMapper();

            JsonNode rootNode = mapper.readTree(aiResponse);

            // Interactions API -> steps -> content -> text
            JsonNode stepsNode = rootNode.path("steps");

            if (!stepsNode.isArray()) {
                throw new RuntimeException("Invalid Gemini response: steps not found");
            }

            String jsonContent = null;

            for (JsonNode step : stepsNode) {

                JsonNode contentNode = step.path("content");

                if (contentNode.isArray()) {

                    for (JsonNode content : contentNode) {

                        if ("text".equals(content.path("type").asText())) {

                            jsonContent = content.path("text").asText()
                                    .replaceFirst("^```json\\s*", "")
                                    .replaceFirst("^```\\s*", "")
                                    .replaceFirst("\\s*```$", "")
                                    .trim();
                            break;
                        }
                    }
                }

                if (jsonContent != null) {
                    break;
                }
            }

            if (jsonContent == null || jsonContent.isBlank()) {
                throw new RuntimeException(
                        "Could not extract recommendation from Gemini response"
                );
            }

            //log.info("Extracted AI JSON successfully: {}", jsonContent);

           JsonNode analysisJson = mapper.readTree(jsonContent);
            JsonNode analysisNode = analysisJson.path("analysis");
            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis,analysisNode,"overall","Overall:");
            addAnalysisSection(fullAnalysis,analysisNode,"pace","Pace:");
            addAnalysisSection(fullAnalysis,analysisNode,"heartRate","Heart Rate:");
            addAnalysisSection(fullAnalysis,analysisNode,"caloriesBurned","Calories Burned:");

            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety = extractSafetyGuidelines(analysisJson.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .type(activity.getType().toString())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {

            log.error("Error processing Gemini response", e);
            e.printStackTrace();
            return createDefaultRecommendation(activity);
        }
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .type(activity.getType().toString())
                .recommendation("Unable to generate detailed analysis")
                .improvements(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness guider"))
                .safety(Arrays.asList(
                        "Always warm up before exercise",
                        "Stay hydrated",
                        "Listen to your body"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafetyGuidelines(JsonNode safetyNode) {
        List<String> safety = new ArrayList<>();
        if(safetyNode.isArray()){
            safetyNode.forEach(item -> safety.add(item.asText()));
        }
        return safety.isEmpty() ?
                Collections.singletonList("Follow general safety guidelines") :
                safety;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions = new ArrayList<>();
        if(suggestionsNode.isArray()){
            suggestionsNode.forEach(suggestion -> {
                String area = suggestion.path("workout").asText();
                String detail = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s",area,detail));

            });
        }
        return suggestions.isEmpty() ?
                Collections.singletonList("No suggestions suggested") :
                suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        List<String> improvements = new ArrayList<>();
        if(improvementsNode.isArray()){
            improvementsNode.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String detail = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s",area,detail));

            });
        }
        return improvements.isEmpty() ?
                Collections.singletonList("No improvements suggested") :
                improvements;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if(!analysisNode.path(key).isMissingNode()){
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        return String.format("""
                        Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format:
                        
                        {
                          "analysis": {
                            "overall": "Overall analysis here",
                            "pace": "Pace analysis here",
                            "heartRate": "Heart rate analysis here",
                            "caloriesBurned": "Calories analysis here"
                          },
                          "improvements": [
                            {
                              "area": "Area name",
                              "recommendation": "Detailed recommendation"
                            }
                          ],
                          "suggestions": [
                            {
                              "workout": "Workout name",
                              "description": "Detailed workout description"
                            }
                          ],
                          "safety": [
                            "Safety point 1",
                            "Safety point 2"
                          ]
                        }
                        
                        Analyze this activity:
                        
                        Activity Type: %s
                        Duration: %d minutes
                        Calories Burned: %d
                        Additional Metrics: %s
                        
                        Provide detailed analysis focusing on:
                        - Overall performance
                        - Pace
                        - Heart rate
                        - Calories burned
                        - Areas for improvement
                        - Next workout suggestions
                        - Safety guidelines
                        
                       Ensure the response follows the EXACT JSON format shown above.
                       """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics()
        );
    }
}