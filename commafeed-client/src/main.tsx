import "@fontsource/open-sans"
import { store } from "app/store"
import dayjs from "dayjs"
import relativeTime from "dayjs/plugin/relativeTime"
import ReactDOM from "react-dom/client"
import { Provider } from "react-redux"
import "swagger-ui-react/swagger-ui.css"
import { App } from "./App"

dayjs.extend(relativeTime)

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
    <Provider store={store}>
        <App />
    </Provider>
)
