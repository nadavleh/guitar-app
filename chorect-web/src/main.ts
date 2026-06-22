import "./style.css";
import { WebAudioEngine } from "./audio";
import { AppState } from "./app/appState";
import { App } from "./app/ui";

const root = document.getElementById("app")!;
const audio = new WebAudioEngine();
const state = new AppState(audio);
new App(state, root);
