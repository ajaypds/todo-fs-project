export const formatDate = (
    value?: string
) => {

    if (!value) {
        return null;
    }

    return new Date(value)
        .toLocaleDateString();
};