package ru.growapp.tftelegram;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.core.config.builder.api.LoggableComponentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.growapp.tftelegram.config.BotConfig;
import ru.growapp.tftelegram.dic.Constants;
import ru.growapp.tftelegram.google.api.GoogleSheetsController;
import ru.growapp.tftelegram.model.Button;
import ru.growapp.tftelegram.model.TFCalendar;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class TelegramBot extends TelegramLongPollingBot {

  Logger logger = LoggerFactory.getLogger(TelegramBot.class);

  @Autowired
  private BotConfig botConfig;

  private final Constants constants = new Constants();

  private final GoogleSheetsController googleSheetsController = new GoogleSheetsController();

  private HashMap<Long, String> lastCommand = new HashMap<>();
  private HashMap<Long, Integer> lastMessageId = new HashMap<>();
  private HashMap<Long, String> selectedStep = new HashMap<>();
  private HashMap<Long, String> selectedInstructor = new HashMap<>();
  private HashMap<Long, String> selectedWorkout = new HashMap<>();
  private HashMap<Long, String> contact = new HashMap<>();
  private final String START_COMMAND = "/start";
  private final String TRIAL_COMMAND = "/trial";
  private final String FEEDBACK_COMMAND = "/feedback";

  private final String CALENDAR_COMMAND = "/calendar";
  private final String TODAY_COMMAND = "/today";
  private final String TOMORROW_COMMAND = "/tomorrow";
  private final String SEVEN_DAYS_COMMAND = "/7days";

  private final String downArrow = "\u2B07";
  private final String leftArrow = "\u2190";

  private final List<String> bans = List.of("null");

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      String messageText = update.getMessage().getText();
      long chatId = update.getMessage().getChatId();
      Long userId = update.getMessage().getFrom().getId();

      if (bans.contains(update.getMessage().getFrom().getUserName())) {
        return;
      }

      switch (messageText) {
        case START_COMMAND:
          lastCommand.put(userId, START_COMMAND);
          selectedInstructor.remove(userId);
          selectedWorkout.remove(userId);
          contact.remove(userId);
          startCommandReceived(
            chatId,
            update.getMessage().getChat().getFirstName(),
            update.getMessage().getChat().getUserName(),
                  userId, true
          );
          break;
        case TRIAL_COMMAND:
          lastCommand.put(userId, TRIAL_COMMAND);
          selectedInstructor.remove(userId);
          selectedWorkout.remove(userId);
          contact.remove(userId);
          trialCommandReceived(chatId, userId);
          break;
        case FEEDBACK_COMMAND:
          lastCommand.put(userId, FEEDBACK_COMMAND);
          selectedInstructor.remove(userId);
          selectedWorkout.remove(userId);
          contact.remove(userId);
          feedbackCommandReceived(chatId, userId);
          break;
        case TODAY_COMMAND:
          lastCommand.put(userId, TODAY_COMMAND);
          calendarCommandReceived(chatId, userId, googleSheetsController.getTodaySpreadsheetData());
          break;
        case TOMORROW_COMMAND:
          lastCommand.put(userId, TOMORROW_COMMAND);
          calendarCommandReceived(chatId, userId, googleSheetsController.getTomorrowSpreadsheetData());
          break;
        case SEVEN_DAYS_COMMAND:
          lastCommand.put(userId, SEVEN_DAYS_COMMAND);
          calendarCommandReceived(chatId, userId, googleSheetsController.get7daysSpreadsheetData());
          break;
        default:
          if (messageText.trim().isEmpty() && !TRIAL_COMMAND.equals(lastCommand.get(userId))) {
            startCommandReceived(chatId,
                    update.getMessage().getChat().getFirstName(),
                    update.getMessage().getChat().getUserName(),
                    userId,
                    false);
          } else if (TRIAL_COMMAND.equals(lastCommand.get(userId))) {
            if (selectedInstructor == null) {
              instructorCommandReceived(chatId, userId);
            } else if (selectedWorkout == null) {
              workoutCommandReceived(chatId, userId);
            } else {
              sendAdminMessage(botConfig.getAdminChatId(), messageText, userId);
              succesCommandReceived(chatId, userId);
            }
          } else if (FEEDBACK_COMMAND.equals(lastCommand.get(userId))) {
            sendFeedback(userId, update.getMessage().getFrom().getUserName(), messageText);
            succesFeedbackReceived(chatId, userId);
          } else {
            startCommandReceived(
                    chatId,
                    update.getMessage().getChat().getFirstName(),
                    update.getMessage().getChat().getUserName(),
                    userId,
                    false
            );
          }
          lastCommand.remove(userId);
      }
    } else if (update.hasCallbackQuery()) {
      String callData = update.getCallbackQuery().getData();
      long chatId = update.getCallbackQuery().getMessage().getChatId();
      Long userId = update.getCallbackQuery().getFrom().getId();

      if (bans.contains(update.getCallbackQuery().getFrom().getUserName())) {
        return;
      }

      if (callData.equals("trial_message")) {
        lastCommand.put(userId, TRIAL_COMMAND);
        selectedInstructor.remove(userId);
        selectedWorkout.remove(userId);
        selectedStep.remove(userId);
        contact.remove(userId);
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("select_class_message") || callData.startsWith("select_instructor_message") || callData.startsWith("skip_instructor_message")) {
        selectedStep.put(userId, callData);
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("instructor_message")) {
        selectedInstructor.put(userId, callData.replace("instructor_message", ""));
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("workout_message")) {
        selectedWorkout.put(userId, constants.getWorkouts().get(Integer.parseInt(callData.replace("workout_message", ""))));
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("feedback_message")) {
        lastCommand.put(userId, FEEDBACK_COMMAND);
        feedbackCommandReceived(chatId, userId);
      } else if (callData.startsWith("calendar_message")) {
        lastCommand.put(userId, CALENDAR_COMMAND);
        calendarCommandReceived(chatId, userId, null);
      } else if (callData.startsWith("today_message")) {
        lastCommand.put(userId, TODAY_COMMAND);
        calendarCommandReceived(chatId, userId, googleSheetsController.getTodaySpreadsheetData());
      } else if (callData.startsWith("tomorrow_message")) {
        lastCommand.put(userId, TOMORROW_COMMAND);
        calendarCommandReceived(chatId, userId, googleSheetsController.getTomorrowSpreadsheetData());
      } else if (callData.startsWith("7days_message")) {
        lastCommand.put(userId, SEVEN_DAYS_COMMAND);
        calendarCommandReceived(chatId, userId, googleSheetsController.get7daysSpreadsheetData());
      } else if (callData.equals("reset_trial_message")) {
        selectedInstructor.remove(userId);
        selectedWorkout.remove(userId);

        String lastStep = selectedStep.get(userId);
        selectedStep.remove(userId);

        if (lastMessageId.get(userId) != null) {
          DeleteMessage deleteMessage = new DeleteMessage();
          deleteMessage.setChatId(chatId);
          deleteMessage.setMessageId(lastMessageId.get(userId));
          try {
            execute(deleteMessage);
          } catch (TelegramApiException e) {
            logger.error("Failed to delete last message", e);
          }
        }

        if (lastStep == null) {
          startCommandReceived(chatId,
                  update.getMessage().getChat().getFirstName(),
                  update.getMessage().getChat().getUserName(),
                  userId,
                  false);
        } else {
          trialCommandReceived(chatId, userId);
        }
      }
    } else if (update.hasMessage() && update.getMessage().getContact() != null) {
      long chatId = update.getMessage().getChatId();
      Long userId = update.getMessage().getFrom().getId();

      if (bans.contains(update.getMessage().getFrom().getUserName())) {
        return;
      }

      contact.put(userId, update.getMessage().getContact().getPhoneNumber() + ", "
              + update.getMessage().getContact().getFirstName() + " "
              + update.getMessage().getContact().getLastName());

      if (selectedWorkout.get(userId) == null) {
        trialCommandReceived(chatId, userId);
      } else {
        sendAdminMessage(botConfig.getAdminChatId(), contact.get(userId), userId);
        succesCommandReceived(chatId, userId);
      }
    }
  }

  private void startCommandReceived(Long chatId, String name, String userName, Long userId, boolean sendStartMessage) {
    String answer =
      "Здравствуйте, " +
      name +
      "! Добро пожаловать в сеть клубов Территория Фитнеса. Чем мы можем Вам помочь?";
    sendMessage(chatId, answer, createStartButtons(), userId);
    if (sendStartMessage) {
      sendMessage(botConfig.getAdminChatId(), "Пользователь @" + userName + " начал использовать бот.", userId);
    }
  }

  private void trialCommandReceived(Long chatId, Long userId) {
    if (selectedStep.get(userId) == null) {
      trialStepCommandReceived(chatId, userId);
    } else {
      if ("select_instructor_message".equals(selectedStep.get(userId)) && selectedInstructor.get(userId) == null) {
        instructorCommandReceived(chatId, userId);
      } else if ("select_class_message".equals(selectedStep.get(userId))) {
        if (selectedWorkout.get(userId) == null) {
          workoutCommandReceived(chatId, userId);
        } else {
          requestInstructor(chatId, userId);
        }
      } else if (selectedWorkout.get(userId) == null) {
        workoutCommandReceived(chatId, userId);
      } else {
        String answer =
                "Выбрана пробная тренировка: <b>" + selectedWorkout.get(userId) + "</b>"
                        + (selectedInstructor.get(userId) != null ? "\nТренер: <b>" + selectedInstructor.get(userId) + "</b>" : "")
                        + "\nДля подтверждения записи укажите, пожалуйста, Ваши контактные данные в обратном сообщении. Например, <i>Имя Фамилия, телефон</i>."
                        + "\nИли нажмите на кнопку <b>Поделиться контактом</b> " + downArrow;
        sendMessage(chatId, answer, createResetButton(), createSubmitButton(), userId);
      }
    }
  }

  private void feedbackCommandReceived(Long chatId, Long userId) {
    String answer = "Напишите, пожалуйста, обратную связь в ответном сообщении.";
    sendMessage(chatId, answer, createResetButton(), userId);
  }

  private void sendFeedback(Long userId, String userName, String message) {
    sendMessage(botConfig.getAdminChatId(), "Получена обратная связь от @" + userName + "\n" + message, userId);
    lastCommand.remove(userId);
  }

  private void trialStepCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите действие:";
    sendMessage(chatId, answer, createTrialStepButtons(), userId);
  }
  private void instructorCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите тренера:";
    sendMessage(chatId, answer, createInstructorButtons(), userId);
  }
  private void requestInstructor(Long chatId, Long userId) {
    String answer = "Выбрать тренера?";
    sendMessage(chatId, answer, createRequestInstructorButtons(), userId);
  }
  private void workoutCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите класс:";
    sendMessage(chatId, answer, createWorkoutButtons(), userId);
  }

  private void defaultCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите доступное действие:";
    sendMessage(chatId, answer, createStartButtons(), userId);
  }
  private void succesCommandReceived(Long chatId, Long userId) {
    String answer = "Спасибо, Ваша заявка зарегистрирована. Мы скоро свяжемся с Вами!";
    sendMessage(chatId, answer, userId);

    selectedWorkout.remove(userId);
    selectedInstructor.remove(userId);
    contact.remove(userId);
    lastCommand.remove(userId);
  }
  private void succesFeedbackReceived(Long chatId, Long userId) {
    String answer = "Спасибо за обратную связь!";
    sendMessage(chatId, answer, userId);

    selectedWorkout.remove(userId);
    selectedInstructor.remove(userId);
    contact.remove(userId);
    lastCommand.remove(userId);
  }

  private void sendAdminMessage(Long chatId, String message, Long userId) {
    String answer = "Новая запись на пробную тренировку: \n" + message;
    if (selectedInstructor.get(userId) != null) {
      answer += "\nТренер: " + selectedInstructor.get(userId);
    }
    if (selectedWorkout.get(userId) != null) {
      answer += "\nКласс: " + selectedWorkout.get(userId);
    }
    sendMessage(chatId, answer, userId);
  }

  private void calendarCommandReceived(Long chatId, Long userId, List<TFCalendar> calendars) {
    if (null == calendars) {
      sendMessage(chatId, "Выберите расписание:", createCalendarButtons(), userId);
    } else {

      StringBuilder answer = new StringBuilder();
      calendars.forEach(c -> {
        if (!answer.isEmpty()) answer.append("\n\n");

        answer.append(String.format("<b>%s %s</b> (%s) - <b>%s</b>", c.getCalDate(), c.getCalTime(), c.getCalDay(), c.getCalWorkout()));
        answer.append("\nТренер: <b>").append(c.getCalInstructor()).append("</b>");
        answer.append("\nЗал: <b>").append(c.getCalZone()).append("</b>");

      });
      sendMessage(chatId, answer.toString(), userId);
    }

    selectedWorkout.remove(userId);
    selectedInstructor.remove(userId);
    contact.remove(userId);
  }

  private void sendMessage(Long chatId, String textToSend, Long userId) {
    sendMessage(chatId, textToSend, null, null, userId);
  }
  private void sendMessage(Long chatId, String textToSend, InlineKeyboardMarkup inlineKeyboardMarkup, Long userId) {
    sendMessage(chatId, textToSend, inlineKeyboardMarkup, null, userId);
  }
  private void sendMessage(Long chatId, String textToSend, ReplyKeyboardMarkup replyKeyboardMarkup, Long userId) {
    sendMessage(chatId, textToSend, null, replyKeyboardMarkup, userId);
  }
  private void sendMessage(Long chatId, String textToSend, InlineKeyboardMarkup inlineKeyboardMarkup, ReplyKeyboardMarkup replyKeyboardMarkup, Long userId) {
    SendMessage sendMessage = new SendMessage();
    sendMessage.enableHtml(true);
    sendMessage.setChatId(String.valueOf(chatId));
    sendMessage.setText(textToSend);
    try {
      if (inlineKeyboardMarkup != null) {
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
      } else if (replyKeyboardMarkup != null) {
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
      }
      Message sentMessage = execute(sendMessage);
      lastMessageId.put(userId, sentMessage.getMessageId());
    } catch (TelegramApiException e) {
      logger.error("Error sending message", e);
    }
  }

  private InlineKeyboardMarkup createStartButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    List<Button> buttons = new ArrayList<>();
    Button trial = new Button();
    trial.setDescription("Бесплатная пробная тренировка");
    trial.setCallBack("trial_message");
    buttons.add(trial);

    Button feedback = new Button();
    feedback.setDescription("Обратная связь");
    feedback.setCallBack("feedback_message");
    buttons.add(feedback);

    Button calendar = new Button();
    calendar.setDescription("Расписание");
    calendar.setCallBack("calendar_message");
    buttons.add(calendar);

    buttons.forEach(b -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(b.getDescription());
      trialButton.setCallbackData(b.getCallBack());
      rowInline.add(trialButton);
      // Set the keyboard to the markup
      rowsInline.add(rowInline);
    });
    // Add it to the message
    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private ReplyKeyboardMarkup createSubmitButton() {
    List<KeyboardButton> buttons = new ArrayList<>();
    KeyboardButton button = new KeyboardButton("Поделиться контактом");
    button.setRequestContact(true);
    buttons.add(button);
    List<KeyboardRow> rows = new ArrayList<>();
    rows.add(new KeyboardRow(buttons));
    ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

    replyKeyboardMarkup.setSelective(true);
    replyKeyboardMarkup.setResizeKeyboard(true);
    replyKeyboardMarkup.setOneTimeKeyboard(true);

    replyKeyboardMarkup.setKeyboard(rows);

    return replyKeyboardMarkup;
  }

  private InlineKeyboardMarkup createTrialStepButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    HashMap<String, String> buttons = new HashMap<>();
    buttons.put("select_class_message", "Выбрать тренировку");
    buttons.put("select_instructor_message", "Выбрать тренера");
    buttons.keySet().forEach(button -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(buttons.get(button));
      trialButton.setCallbackData(button);
      rowInline.add(trialButton);
      rowsInline.add(rowInline);
    });

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }
  private InlineKeyboardMarkup createResetButton() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }
  private InlineKeyboardMarkup createInstructorButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    constants.getInstructors().forEach(instructor -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(instructor);
      trialButton.setCallbackData("instructor_message"+instructor);
      rowInline.add(trialButton);
      rowsInline.add(rowInline);
    });

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }
  private InlineKeyboardMarkup createRequestInstructorButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    List<InlineKeyboardButton> rowInline = new ArrayList<>();

    InlineKeyboardButton yesButton = new InlineKeyboardButton();
    yesButton.setText("Да");
    yesButton.setCallbackData("select_instructor_message");
    rowInline.add(yesButton);

    InlineKeyboardButton noButton = new InlineKeyboardButton();
    noButton.setText("Нет");
    noButton.setCallbackData("skip_instructor_message");
    rowInline.add(noButton);

    rowsInline.add(rowInline);
    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private InlineKeyboardMarkup createCalendarButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    List<InlineKeyboardButton> rowInline1 = new ArrayList<>();
    InlineKeyboardButton todayButton = new InlineKeyboardButton();
    todayButton.setText("Расписание на сегодня");
    todayButton.setCallbackData("today_message");
    rowInline1.add(todayButton);

    List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
    InlineKeyboardButton tomorrowButton = new InlineKeyboardButton();
    tomorrowButton.setText("Расписание на завтра");
    tomorrowButton.setCallbackData("tomorrow_message");
    rowInline2.add(tomorrowButton);

    List<InlineKeyboardButton> rowInline3 = new ArrayList<>();
    InlineKeyboardButton sevenDaysButton = new InlineKeyboardButton();
    sevenDaysButton.setText("Расписание на 7 дней");
    sevenDaysButton.setCallbackData("7days_message");
    rowInline3.add(sevenDaysButton);

    rowsInline.add(rowInline1);
    rowsInline.add(rowInline2);
    rowsInline.add(rowInline3);
    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private InlineKeyboardMarkup createWorkoutButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    IntStream.range(0, constants.getWorkouts().size()).forEach(index -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(constants.getWorkouts().get(index));
      trialButton.setCallbackData("workout_message"+index);
      rowInline.add(trialButton);
      rowsInline.add(rowInline);
    });

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private List<InlineKeyboardButton> addResetButton() {
    List<InlineKeyboardButton> rowInline = new ArrayList<>();
    InlineKeyboardButton resetButton = new InlineKeyboardButton();
    resetButton.setText(leftArrow + " Назад");
    resetButton.setCallbackData("reset_trial_message");
    rowInline.add(resetButton);

    return rowInline;
  }

  @Override
  public String getBotUsername() {
    return botConfig.getBotName();
  }

  @Override
  public String getBotToken() {
    return botConfig.getToken();
  }
}
