package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.stage.Window;

/** A desktop that records what it was asked and never opens a browser or a native dialog. */
final class FakeHost implements Host {

    final List<String> opened = new CopyOnWriteArrayList<>();
    volatile int exits;
    volatile Path saveAnswer;
    volatile Path openAnswer;
    /** The questions asked, and the answer the next one gets (the capture hook). */
    final List<Confirmations.Question> questions = new CopyOnWriteArrayList<>();

    volatile boolean confirmAnswer = true;
    /** Runs on each question before it is answered: a test unticks a choice here. */
    volatile java.util.function.Consumer<Confirmations.Question> onQuestion = q -> {};

    @Override
    public void openUrl(String url) {
        opened.add(url);
    }

    @Override
    public Path chooseSaveFile(Window owner, String title, String initialName) {
        return saveAnswer;
    }

    @Override
    public Path chooseOpenFile(Window owner, String title) {
        return openAnswer;
    }

    @Override
    public boolean confirm(Window owner, Confirmations.Question question) {
        questions.add(question);
        onQuestion.accept(question);
        return confirmAnswer;
    }

    @Override
    public void exit() {
        exits++;
    }
}
