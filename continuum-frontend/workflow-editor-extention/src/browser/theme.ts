import { createTheme } from '@mui/material/styles';


const theme = createTheme({
  palette: {
    mode: 'light',
    // primary: {
    //   main: "#ce93d8",
    //   light: "#f3e5f5",
    //   dark: "#ab47bc",
    //   contrastText: "rgba(0, 0, 0, 0.87)"
    // },
    // secondary: {
    //   main: "#0D98BA",
    //   light: "#5DD6F4",
    //   dark: "#042F39",
    //   contrastText: "rgba(0, 0, 0, 0.87)"
    // }
  },
  components: {
    MuiAlert: {
      styleOverrides: {
        root: ({ ownerState }) => ({
          ...(ownerState.severity === 'info' && {
            backgroundColor: '#60a5fa',
          }),
        }),
      },
    },
  },
});

export default theme;