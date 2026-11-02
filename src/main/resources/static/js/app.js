'use strict';
document.querySelectorAll('[data-password]').forEach(button => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.password);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? 'Hide' : 'Show';
    button.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    button.setAttribute('aria-pressed', String(show));
  });
});
document.querySelectorAll('form[data-confirm]').forEach(form => {
  form.addEventListener('submit', event => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});

document.querySelectorAll('[data-copy-meeting]').forEach(button=>button.addEventListener('click',async()=>{const link=document.getElementById('meeting-link'),status=document.getElementById('copy-status');try{await navigator.clipboard.writeText(link.value);status.textContent='Meeting link copied.';}catch(e){link.select();status.textContent='Select and copy the link above.';}}));
