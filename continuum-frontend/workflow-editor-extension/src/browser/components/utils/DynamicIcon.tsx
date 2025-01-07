import React from 'react';
import SVG from 'react-inlinesvg';
import QuestionMarkIcon from '@mui/icons-material/QuestionMark';
import { SvgIcon, SvgIconProps } from '@mui/material';
import { AppContext } from '../context/AppContextProvider';

export default function DynamicIcon({icon, ...others}: SvgIconProps & {icon?: string}) {
    const { iconBank } = React.useContext(AppContext);
    icon = icon?.trim();
    if (icon) {
        if(icon.toLowerCase().startsWith("<svg")){
            return (
                <SvgIcon {...others}>
                    <SVG src={icon}/>
                </SvgIcon>
            );
        }
        const [category, iconName] = icon.split("/");
        const Icon = iconBank[category][iconName];
        return (
            <Icon {...others}/>
        );
    }
    return <QuestionMarkIcon/>;
}