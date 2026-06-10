export const formatDate = (
    value?: string | null | undefined
) => {

    if (!value) {
        return null;
    }

    return new Date(value)
        .toLocaleDateString();
};

// export const formattedDate = (value?: string | null | undefined) => {
//     return value
//         ? new Date(value)
//             .toLocaleDateString("en-GB")
//             .replace(/\//g, "-")
//         : "";
// }