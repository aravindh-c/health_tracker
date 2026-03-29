# Health Track — App Feature Documentation

A family nutrition tracking Android app (Kotlin) with AI-powered meal analysis, built for personalized health management using OpenAI GPT-4o or Claude as the AI backend and Google Sheets as the data store.

---

## Table of Contents

1. [App Overview](#1-app-overview)
2. [User Profiles](#2-user-profiles)
3. [Screens & Features](#3-screens--features)
   - [User Selection](#31-user-selection)
   - [Food Log](#32-food-log)
   - [Food History](#33-food-history)
   - [Report & Analytics](#34-report--analytics)
   - [Meal Suggestions (Tips)](#35-meal-suggestions-tips)
   - [Settings](#36-settings)
4. [AI / LLM Integration](#4-ai--llm-integration)
5. [Google Sheets Integration](#5-google-sheets-integration)
6. [Scoring System](#6-scoring-system)
7. [Notifications](#7-notifications)
8. [Food Preference Learning](#8-food-preference-learning)
9. [Data Flow](#9-data-flow)
10. [LLM Call Summary](#10-llm-call-summary)

---

## 1. App Overview

| Property | Detail |
|----------|--------|
| Platform | Android (Kotlin, minSdk 26) |
| AI Backends | OpenAI GPT-4o **or** Anthropic Claude Sonnet 4.6 (switchable) |
| Data Storage | Google Sheets via Apps Script web app |
| Users | Multiple family members (Aravindh, Deepa) |
| Auth | API key stored in Android EncryptedSharedPreferences |
| Timezone | IST (Asia/Kolkata) used throughout |

---

## 2. User Profiles

Two pre-configured family members with detailed medical context:

### Aravindh (Primary)
- Age 32 | 86 kg | 171 cm
- **Conditions**: Prediabetes, Fatty Liver Grade 1, Elevated Uric Acid, Kidney Stone History
- **Daily Targets**: 1800 kcal | 100g protein | 30g fiber | 60g fat | <40g simple carbs | 85g complex carbs
- **Weight goal**: 82 kg (chart range: 70–90 kg)
- **Diet style**: Non-veg, South Indian — red rice, chapathi, eggs, chicken, fish

### Deepa (Wife)
- Age 31 | 63 kg | 149 cm
- **Conditions**: Overweight, Severe Vitamin D Deficiency, Iron Deficiency, Migraines, Sedentary lifestyle
- **Symptoms**: Headaches, leg pain, tiredness, bloating
- **Risk flags**: Tea intake limits iron absorption; sedentary lifestyle
- **Daily Targets**: 1250 kcal | 70g protein | 25g fiber | 45g fat | <35g simple carbs | 85g complex carbs
- **Weight goal**: 58 kg (chart range: 50–70 kg)
- **Diet style**: Non-veg, South Indian — idli, dosa, sambar, spinach, eggs

Each profile carries:
- Lab report values (HbA1c, LDL, Vitamin D, Ferritin, etc.)
- Health goals (weight loss, reduce fatty liver, etc.)
- Detailed food preferences JSON (preferred foods, protein sources, carb sources, snacks, vegetables)

---

## 3. Screens & Features

### 3.1 User Selection

Entry point of the app.

- Displays all family members as selectable cards (name, age, medical conditions)
- First-launch API key setup dialog (enter OpenAI or Claude key)
- Stores selected user ID in encrypted preferences
- Supports switching between users at any time

---

### 3.2 Food Log

The primary daily-use screen.

#### Date Selection
- **Today** chip (default, IST date)
- **Yesterday** chip
- **Pick Date** chip — opens calendar picker (past dates only, no future dates allowed)

#### Meal Logging
- **Meal type dropdown**: Gym Pre-Workout, Breakfast, Mid-Day Snack, Lunch, Evening Snack, Dinner, Gym Workout (Burn), Other
- **Food input**: Free-text natural language (e.g., `"2 idli with sambar and fried egg"`)
  - When **Gym Workout (Burn)** is selected, input label changes to "Calories burned" — no LLM call, directly logs negative calories
- **Log Meal button**: Triggers AI nutrient estimation

#### After Logging — Result Card
- Shows **6 nutrients**: Protein, Fat, Fiber, Calories, Simple Carbs, Complex Carbs
- Shows **AI-generated insight**: 2–3 plain sentences about the meal quality, remaining budget, and one tip for next meal
- Shows **motivational popup**: Context-aware message based on meal content vs daily targets
  - *Positive*: if good protein + reasonable calories
  - *Correction*: if single meal exceeds 55% of daily calorie target
  - *Protein Low*: if meal has <5g protein
  - *Encouragement*: default fallback
- Result card **persists** when navigating to other tabs and back

#### Gym Workout (Burn) Mode
- Enter calories burned as a positive number
- Logged as `calories_kcal = -(entered value)` in NutrientLog
- Net daily calories = food consumed − gym burned
- Message: `"X kcal gym burn logged. Your net calories for today are adjusted."`

---

### 3.3 Food History

- Displays today's logged meals as a list
- Each row shows: meal type, food description, timestamp
- Refresh button to reload from Google Sheets

---

### 3.4 Report & Analytics

#### Daily Nutrition Summary Card
- Protein / Fat / Fiber / Calories vs daily targets
- Simple Carbs (with max limit) and Complex Carbs

#### Daily Nutrition Score
- Single score (0–100%) with progress bar
- Calculated from weighted nutrient achievement + penalties (see [Scoring System](#6-scoring-system))

#### Today vs Target Bar Chart (6 bars)
Shows each nutrient as % of target:

| Bar | Color Logic |
|-----|-------------|
| Complex Carbs | Green ≥80%, Orange ≥50%, Red <50% |
| Simple Carbs | Red if >110% (punishment), else green/orange/red |
| Fat | Red if >110%, else green/orange/red |
| Fiber | Green ≥80%, Orange ≥50%, Red <50% |
| Calories | Red if >110%, else green/orange/red |
| Protein | Green ≥80%, Orange ≥50%, Red <50% |

Y-axis: −100% to 200% with target line at 100%

#### Monthly Trend Line Chart
- 5 lines: Calories (green), Protein (blue), Fiber (orange), Carbs combined (purple), Fat (red)
- Month picker: last 6 months
- All data fetched in parallel (one request per day of month)
- Y-axis: 0–150% of target

#### Weight Tracker
- Input field + Log button to record daily weight (kg)
- Line chart with:
  - User-specific Y-axis (Aravindh: 70–90 kg, Deepa: 50–70 kg)
  - 100g granularity on Y-axis
  - Orange dashed target weight line
- Weight history loaded from Google Sheets (WeightLog tab)

---

### 3.5 Meal Suggestions (Tips)

#### Meal Progress Bar
- Chips for each meal type showing ✓ (logged) or pending
- Distinct visual for logged vs remaining meals

#### Get Suggestions Button
Calls the LLM with full context to generate personalized meal suggestions:

**Context sent to LLM:**
- Today's consumed nutrients vs targets (with remaining gaps)
- User's medical conditions and health goals
- Food preferences JSON (preferred foods, protein sources, carb sources, snacks, vegetables, etc.)
- 7-day weekly average nutrients (to address recurring deficiencies)
- Last 3 previous suggestions (to avoid repetition)

**Rules enforced in prompt:**
- ONLY suggest foods explicitly listed in the user's food preferences JSON
- Do NOT repeat meals from recent suggestions
- Strictly avoid foods that worsen user's medical conditions
- One meal suggestion per remaining meal type
- South Indian / Indian home food style

**Output format:**
```
You need ~X kcal, Xg protein, Xg fiber more today.

**[Meal Type]: [Meal Name]**
• [food item] — [health reason + rough cal/protein]
• [food item] — [health reason]
```
Max 3 bullets per meal.

#### Suggestion Memory
- Last 5 suggestions saved per user (truncated to 300 chars each)
- Used in next call's prompt to avoid repetition

---

### 3.6 Settings

- **API Key**: Masked display (shows only last 4 characters); editable
- **LLM Provider**: Dropdown to switch between OpenAI GPT-4o and Claude Sonnet 4.6
  - Change takes effect immediately — no app restart needed
- **Biometric authentication** prompt before saving changes (if device has PIN/biometric)
- **Export Food Preferences**: Downloads user's current food preference JSON to device Downloads folder

---

## 4. AI / LLM Integration

### Supported Providers
| Provider | Model | API Endpoint |
|----------|-------|--------------|
| OpenAI | GPT-4o | `api.openai.com/v1/chat/completions` |
| Claude | claude-sonnet-4-6 | `api.anthropic.com/v1/messages` |

Both implement the same `LlmService` interface — swapping providers requires no code changes, just selecting in Settings.

### LLM Call Types

#### 1. Nutrient Estimation (`estimateNutrients`)
- **When**: Every meal log (except Gym Workout Burn)
- **Input**: Food text + full user medical profile
- **Output**: JSON with 6 nutrient values
- **Rules in prompt**:
  - Interpret natural descriptions ("little rice", "small bowl", "2 idli")
  - Estimate portions if quantity missing
  - Split carbs: simple (sugars, sweets, fruit) vs complex (rice, wheat, oats, lentils)
  - Fiber from grains, vegetables, legumes
- **Tokens**: ~600

#### 2. Post-Meal Insights (`getInsights`)
- **When**: After every meal log (except gym burn)
- **Input**: This meal's nutrients + today's running total + user profile
- **Output**: 2–3 plain-text sentences (no markdown):
  1. Celebrate one win OR flag biggest concern
  2. Remaining calorie/protein budget for the day
  3. One specific tip for the next meal
- **Limit**: <45 words, max 120 tokens

#### 3. Meal Suggestions (`getMealSuggestions`)
- **When**: User taps "Get Suggestions" on Tips screen
- **Input**: Consumed nutrients, targets, remaining meal types, food preferences, 7-day averages, recent suggestions
- **Output**: Formatted suggestion block (see Tips screen section)
- **Tokens**: ~1000

#### 4. Food Health Check (`isFoodHealthyForUser`)
- **When**: Background, auto-triggered when a food is logged 5+ times
- **Input**: Food name + user's medical conditions
- **Output**: YES or NO
- **Tokens**: 5 — very cheap call

---

## 5. Google Sheets Integration

### Sheet Structure

**FoodLog** tab (5 columns):
| date | user_id | meal_type | food_text | timestamp |
|------|---------|-----------|-----------|-----------|

**NutrientLog** tab (10 columns):
| date | user_id | meal_type | protein_g | fat_g | fiber_g | carbs_simple_g | carbs_complex_g | calories_kcal | timestamp |

**WeightLog** tab (3 columns):
| date | user_id | weight_kg |

### Key Technical Notes
- Apps Script deployed as web app — Android calls it via HTTPS POST/GET
- **Date column format**: `setNumberFormat("@")` applied before every `setValues()` on all 3 sheets to prevent Google Sheets from auto-converting ISO date strings (`2026-03-15`) to Date serial numbers
- `PostRedirectInterceptor` in OkHttp handles the Apps Script POST → redirect → re-POST flow
- Daily report aggregates NutrientLog rows by user + date, summing all nutrient columns
- Gym burns stored as negative `calories_kcal` — net daily total naturally subtracts them

---

## 6. Scoring System

Daily Nutrition Score (0–100%, can go negative before UI clamping):

| Nutrient | Weight | Scoring Logic |
|----------|--------|---------------|
| Protein | 30% | 0–100% of target → 0–100 pts. Over target = full 100 pts (no penalty) |
| Fiber | 20% | Same as protein — no penalty for excess |
| Calories | 20% | 90–110% of target = 100 pts. Under 90% ramps proportionally. Over 110% = −10 pts per 1% over |
| Simple Carbs | 20% | Under target = 100 pts. Over target = −4 pts per 1% over (strictest penalty) |
| Fat | 10% | Under target = 100 pts. Over target = −2.5 pts per 1% over |

**Score = sum of (component score × weight)**

UI behaviour:
- `tvScore`: Shows raw calculated value (can be negative)
- `progressScore`: Clamped to 0–100 for the progress bar

---

## 7. Notifications

Four daily scheduled reminder slots using WorkManager:

| Time | Slot Key | Theme |
|------|----------|-------|
| 07:30 | `morning_motivation` | Start the day strong |
| 12:45 | `meal_reminders` | Log your meal |
| 16:30 | `snack_discipline` | Choose healthy snacks |
| 20:30 | `evening_reflection` | Reflect on today |

### How it works
- Scheduled once on first app launch (permission required on Android 13+)
- `KEEP` policy — no duplicate scheduling across reboots
- Each `MealReminderWorker` fires the notification then reschedules itself for the next day
- Messages loaded from `notifications.json` asset — random selection per category

### Message personalization
Messages support token substitution:
- `{name}` → active user's display name
- `{spouse_name}` → partner's name
- `{child_name}` → child's name

### Notification categories in `notifications.json`
- `morning_motivation`
- `meal_reminders`
- `snack_discipline`
- `evening_reflection`
- `family_motivation`
- `popup_positive` / `popup_correction` / `popup_protein_low` / `popup_encouragement` (in-app popups)

---

## 8. Food Preference Learning

The app automatically learns and expands each user's food preferences over time:

### How it works

1. **Every meal logged** → `FoodPreferenceManager.trackFoodText()` tokenizes the food text and increments a per-food frequency counter stored in internal storage

2. **After 5 logs of the same food** (threshold) → food is flagged as "new frequent food"

3. **Background LLM health check** (fire-and-forget) → `isFoodHealthyForUser(food, userProfile)` asks the LLM if the food is safe given the user's medical conditions

4. **If healthy** → food is automatically added to the user's preferences JSON

5. **Next suggestion call** picks it up as a valid food option

### Tokenization rules
- Splits on: `,`, `+`, `and`, `with`
- Removes: quantities (`2`, `small`, `big`, `little`, `cup`, `bowl`, `plate`, `g`, `ml`)
- Result: individual food names tracked independently

### Preference file priority
1. User-edited file in internal storage (if exists)
2. Default asset file (`food_preferences_{userId}.json`)

### Export
Settings screen → Export Food Preferences → saves JSON to device Downloads folder

---

## 9. Data Flow

```
User logs a meal
        │
        ▼
FoodLogFragment → FoodLogViewModel.logMeal(mealType, foodText, date)
        │
        ▼
NutritionRepository.logMeal()
        ├─── [If Gym Burn] ──────────────────────────────────────────────────────┐
        │    logGymActivity() → logNutrients(calories = -burned)                │
        │    Return "X kcal gym burn logged"                                    │
        │                                                                        │
        ├─── [Normal Meal] ──────────────────────────────────────────────────────┤
        │    SheetsService.logFood()           → FoodLog sheet                  │
        │    LlmService.estimateNutrients()    → OpenAI / Claude API            │
        │    SheetsService.logNutrients()      → NutrientLog sheet              │
        │    FoodPreferenceManager.trackFoodText()                               │
        │    [Background] autoUpdateFoodPreferences()                            │
        │         └── getNewFrequentFoods() → isFoodHealthyForUser() → add      │
        │    SheetsService.getDailyReport()    → fetch today's running total    │
        │    LlmService.getInsights()          → 2-3 sentence meal insight      │
        │    Return MealLogResult(nutrients, insights)                           │
        │                                                                        │
        ▼                                                                        ▼
FoodLogFragment displays:
  • Nutrient breakdown card
  • LLM insight text
  • Motivational popup (PopupMessageHelper)


Report Tab — ReportViewModel.load()
        ├─── SheetsService.getDailyReport()   → NutrientData for today
        ├─── ScoreCalculator.calculate()      → DailyScore
        ├─── [Monthly] getMonthlyData()       → parallel fetch all days
        └─── [Weight] getWeightHistory()      → WeightLog sheet


Tips Tab — TipsViewModel.loadSuggestions()
        ├─── SheetsService.getDailyReport()   → today consumed
        ├─── SheetsService.getFoodHistory()   → logged meal types
        ├─── getWeeklyAverages()              → 7-day parallel fetch
        ├─── SuggestionMemory.getRecent()     → last 3 suggestions
        ├─── LlmService.getMealSuggestions()  → formatted suggestions
        └─── SuggestionMemory.save()          → store for deduplication


Notifications — WorkManager (background)
        ├─── 07:30 → morning_motivation
        ├─── 12:45 → meal_reminders
        ├─── 16:30 → snack_discipline
        └─── 20:30 → evening_reflection (each reschedules for next day)
```

---

## 10. LLM Call Summary

Per full day (all meals logged + one suggestion fetch):

| Action | Calls | When |
|--------|-------|------|
| `estimateNutrients` | 1 per meal | Each meal log |
| `getInsights` | 1 per meal | After each meal log |
| `getMealSuggestions` | 1 | When user taps "Get Suggestions" |
| `isFoodHealthyForUser` | 0–N | Background, only when food logged 5+ times |

**Typical day (4–5 meals + 1 suggestion):** ~9–11 LLM calls per user

> **Optimization note**: `estimateNutrients` + `getInsights` could be merged into a single call (extract JSON + text from one response), reducing meal-log calls from 2 to 1 — ~50% cost saving on meal logging. Not yet implemented.
