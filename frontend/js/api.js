// KitchenSense API Configuration
const API_BASE = 'https://udhw46pu1c.execute-api.ap-south-1.amazonaws.com/prod';

function getUserId() {
    return localStorage.getItem('userId') || 'user001';
}

function getStoredUserName() {
    return localStorage.getItem('userName') || 'User';
}

function getUserInitials(name) {
    const words = name.trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) return '?';
    if (words.length === 1) return words[0].substring(0, 2).toUpperCase();
    return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

async function handleApiResponse(response) {
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || data.error || `Request failed with status ${response.status}`);
    }
    return data;
}

function initializeSidebarUser() {
    const name = getStoredUserName();
    const sidebarName = document.getElementById('sidebarName');
    const sidebarAvatar = document.getElementById('sidebarAvatar');

    if (sidebarName) sidebarName.textContent = name;
    if (sidebarAvatar) sidebarAvatar.textContent = getUserInitials(name);
}

function mapFridgeStatus(localValue) {
    const map = {
        'standard': 'FUNCTIONAL',
        'adjusted': 'OLD',
        'pantry': 'NONE'
    };
    return map[localValue] || 'FUNCTIONAL';
}

async function apiAddItem(item) {
    item.fridgeStatus = mapFridgeStatus(localStorage.getItem('fridgeStatus') || 'standard');
    
    const response = await fetch(
        `${API_BASE}/items?userId=${getUserId()}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(item)
    });
    return handleApiResponse(response);
}

async function apiGetItems() {
    const response = await fetch(
        `${API_BASE}/items?userId=${getUserId()}`);
    return handleApiResponse(response);
}

async function apiDeleteItem(itemId) {
    const response = await fetch(
        `${API_BASE}/items/${itemId}?userId=${getUserId()}`, {
        method: 'DELETE'
    });
    return handleApiResponse(response);
}

async function apiGetRecipe(fridgeStatus) {
    const mapped = mapFridgeStatus(fridgeStatus);
    const response = await fetch(
        `${API_BASE}/recipe?userId=${getUserId()}&fridgeStatus=${mapped}`);
    return handleApiResponse(response);
}

async function apiChat(message, fridgeStatus) {
    const mapped = mapFridgeStatus(fridgeStatus);
    const response = await fetch(
        `${API_BASE}/chat?userId=${getUserId()}&fridgeStatus=${mapped}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: message })
    });
    return handleApiResponse(response);
}